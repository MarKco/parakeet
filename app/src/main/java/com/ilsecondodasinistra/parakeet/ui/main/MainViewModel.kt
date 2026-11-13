package com.ilsecondodasinistra.parakeet.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilsecondodasinistra.parakeet.alarm.AlarmScheduler
import com.ilsecondodasinistra.parakeet.alarm.ExitNotificationScheduler
import com.ilsecondodasinistra.parakeet.data.ActiveAlarm
import com.ilsecondodasinistra.parakeet.data.LegacyMigration
import com.ilsecondodasinistra.parakeet.data.SettingsRepository
import com.ilsecondodasinistra.parakeet.data.TaskRepository
import com.ilsecondodasinistra.parakeet.domain.Task
import com.ilsecondodasinistra.parakeet.domain.WakeUpCalculator
import com.ilsecondodasinistra.parakeet.domain.WakeUpPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class AlarmStatus(val wakeAt: LocalDateTime, val exitAt: LocalDateTime)

data class MainUiState(
    val ready: Boolean = false,
    val showOnboarding: Boolean = false,
    val showWhatsNew: Boolean = false,
    val tasks: List<Task> = emptyList(),
    val exitTime: LocalTime? = null,
    val plan: WakeUpPlan? = null,
    val alarm: AlarmStatus? = null,
    val notifyOnExit: Boolean = true,
    val exitText: String = "",
)

sealed interface MainEvent {
    data class AlarmSet(val wakeAt: LocalDateTime) : MainEvent
    data object NoClockApp : MainEvent
    data object AlarmCancelled : MainEvent
    data class TaskRemoved(val task: Task) : MainEvent
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val migration: LegacyMigration,
    private val alarmScheduler: AlarmScheduler,
    private val exitNotifications: ExitNotificationScheduler,
    private val clock: Clock,
) : ViewModel() {

    private val startup = MutableStateFlow(Startup())
    private val _events = MutableSharedFlow<MainEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<MainEvent> = _events.asSharedFlow()

    private data class Startup(val ready: Boolean = false, val showWhatsNew: Boolean = false)

    /** Emits the current time every 30 s so "today/tomorrow" and the plan stay fresh while the app is open. */
    private val ticker = flow {
        while (true) {
            emit(LocalDateTime.now(clock))
            delay(30_000)
        }
    }

    val state: StateFlow<MainUiState> = combine(
        tasks.tasks, settings.settings, startup, ticker,
    ) { taskList, s, start, now ->
        val exitTime = s.exitTime
        val alarm = s.activeAlarm
            ?.takeIf { it.exitAtMillis > System.currentTimeMillis() }
            ?.let { AlarmStatus(it.wakeAtMillis.toLocal(), it.exitAtMillis.toLocal()) }
        MainUiState(
            ready = start.ready,
            showOnboarding = start.ready && !s.onboardingDone,
            showWhatsNew = start.showWhatsNew,
            tasks = taskList,
            exitTime = exitTime,
            plan = exitTime?.let { WakeUpCalculator.plan(it, taskList, now) },
            alarm = alarm,
            notifyOnExit = s.notifyOnExit,
            exitText = s.exitText,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch {
            val result = migration.migrateIfNeeded()
            startup.value = Startup(ready = true, showWhatsNew = result.wasExistingUser)
        }
    }

    fun dismissWhatsNew() {
        startup.value = startup.value.copy(showWhatsNew = false)
    }

    // ---- exit time & tasks -------------------------------------------------------------------

    fun setExitTime(time: LocalTime) = viewModelScope.launch { settings.setExitTime(time) }

    fun toggleTask(task: Task) = viewModelScope.launch { tasks.setChecked(task.id, !task.checked) }

    fun addTask(name: String, minutes: Int) = viewModelScope.launch { tasks.add(name, minutes) }

    fun updateTask(task: Task) = viewModelScope.launch { tasks.update(task) }

    fun removeTask(task: Task) = viewModelScope.launch {
        tasks.delete(task.id)
        _events.emit(MainEvent.TaskRemoved(task))
    }

    fun restoreTask(task: Task) = viewModelScope.launch { tasks.restore(task) }

    // ---- alarm ---------------------------------------------------------------------------------

    fun setAlarm() = viewModelScope.launch {
        val plan = state.value.plan ?: return@launch
        if (plan.totalMinutes == 0) return@launch
        // Avoid duplicates: dismiss the alarm we created previously, if any
        if (settings.current().activeAlarm != null) alarmScheduler.cancel()

        if (!alarmScheduler.set(plan.wakeAt)) {
            _events.emit(MainEvent.NoClockApp)
            return@launch
        }
        val exitMillis = plan.exitAt.toMillis()
        settings.setActiveAlarm(ActiveAlarm(plan.wakeAt.toMillis(), exitMillis))
        if (settings.current().notifyOnExit) exitNotifications.schedule(exitMillis) else exitNotifications.cancel()
        _events.emit(MainEvent.AlarmSet(plan.wakeAt))
    }

    fun cancelAlarm() = viewModelScope.launch {
        alarmScheduler.cancel()
        exitNotifications.cancel()
        settings.setActiveAlarm(null)
        _events.emit(MainEvent.AlarmCancelled)
    }

    // ---- onboarding & settings ----------------------------------------------------------------

    fun completeOnboarding(exitTime: LocalTime, chosen: List<Pair<String, Int>>, notify: Boolean) =
        viewModelScope.launch {
            settings.setExitTime(exitTime)
            if (tasks.count() == 0) chosen.forEach { (name, minutes) -> tasks.add(name, minutes) }
            settings.setNotifyOnExit(notify)
            settings.setOnboardingDone()
        }

    fun setNotifyOnExit(enabled: Boolean) = viewModelScope.launch {
        settings.setNotifyOnExit(enabled)
        val alarm = settings.current().activeAlarm
        if (!enabled) exitNotifications.cancel()
        else if (alarm != null) exitNotifications.schedule(alarm.exitAtMillis)
    }

    fun setExitText(text: String) = viewModelScope.launch { settings.setExitText(text) }

    fun canScheduleExact(): Boolean = exitNotifications.canScheduleExact()

    /** Called when exact-alarm access may have changed, to re-arm the notification with the right precision. */
    fun refreshExitNotification() = viewModelScope.launch {
        val s = settings.current()
        val alarm = s.activeAlarm
        if (s.notifyOnExit && alarm != null) exitNotifications.schedule(alarm.exitAtMillis)
    }

    private fun Long.toLocal(): LocalDateTime =
        java.time.Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

    private fun LocalDateTime.toMillis(): Long =
        atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
