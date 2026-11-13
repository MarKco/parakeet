package com.ilsecondodasinistra.parakeet.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilsecondodasinistra.parakeet.alarm.AlarmScheduler
import com.ilsecondodasinistra.parakeet.alarm.ExitNotificationScheduler
import com.ilsecondodasinistra.parakeet.alarm.InternalAlarmScheduler
import com.ilsecondodasinistra.parakeet.data.ActiveAlarm
import com.ilsecondodasinistra.parakeet.data.AlarmMode
import com.ilsecondodasinistra.parakeet.data.RingingPrefs
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
    val showAlarmIntro: Boolean = false,
    val tasks: List<Task> = emptyList(),
    val exitTime: LocalTime? = null,
    val plan: WakeUpPlan? = null,
    val alarm: AlarmStatus? = null,
    val notifyOnExit: Boolean = true,
    val exitText: String = "",
    val alarmMode: AlarmMode = AlarmMode.CLOCK,
    val ringing: RingingPrefs = RingingPrefs(),
)

sealed interface MainEvent {
    data class AlarmSet(val wakeAt: LocalDateTime) : MainEvent
    data object NoClockApp : MainEvent
    data object NeedsExactAlarm : MainEvent
    data object AlarmCancelled : MainEvent
    data class TaskRemoved(val task: Task) : MainEvent
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val migration: LegacyMigration,
    private val alarmScheduler: AlarmScheduler,
    private val internalAlarm: InternalAlarmScheduler,
    private val exitNotifications: ExitNotificationScheduler,
    private val clock: Clock,
) : ViewModel() {

    private val startup = MutableStateFlow(Startup())
    private val _events = MutableSharedFlow<MainEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<MainEvent> = _events.asSharedFlow()

    private data class Startup(val ready: Boolean = false)

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
            showAlarmIntro = start.ready && s.onboardingDone && !s.alarmIntroSeen,
            tasks = taskList,
            exitTime = exitTime,
            plan = exitTime?.let { WakeUpCalculator.plan(it, taskList, now) },
            alarm = alarm,
            notifyOnExit = s.notifyOnExit,
            exitText = s.exitText,
            alarmMode = s.alarmMode,
            ringing = s.ringing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch {
            migration.migrateIfNeeded()
            startup.value = Startup(ready = true)
        }
    }

    /** Answer to the "new in 5.0" card: try the Parakeet alarm or keep the Clock app. */
    fun answerAlarmIntro(useParakeetAlarm: Boolean) = viewModelScope.launch {
        if (useParakeetAlarm && canScheduleExactAlarms()) switchAlarmMode(AlarmMode.INTERNAL) else settings.setAlarmIntroSeen()
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
            _events.emit(if (settings.current().alarmMode == AlarmMode.INTERNAL) MainEvent.NeedsExactAlarm else MainEvent.NoClockApp)
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

    fun completeOnboarding(exitTime: LocalTime, chosen: List<Pair<String, Int>>, notify: Boolean, internalAlarm: Boolean) =
        viewModelScope.launch {
            settings.setAlarmMode(if (internalAlarm && canScheduleExactAlarms()) AlarmMode.INTERNAL else AlarmMode.CLOCK)
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

    fun canScheduleExactAlarms(): Boolean = exitNotifications.canScheduleExact()

    fun setAlarmMode(mode: AlarmMode) = viewModelScope.launch { switchAlarmMode(mode) }

    /** Switches between Clock app and in-app alarm; an alarm already set is moved to the new mode. */
    private suspend fun switchAlarmMode(mode: AlarmMode) {
        val s = settings.current()
        if (s.alarmMode == mode) { settings.setAlarmMode(mode); return }
        val active = s.activeAlarm?.takeIf { it.wakeAtMillis > System.currentTimeMillis() }
        if (active != null) alarmScheduler.cancel()
        settings.setAlarmMode(mode)
        if (active != null && !alarmScheduler.set(active.wakeAtMillis.toLocal())) settings.setActiveAlarm(null)
    }

    fun updateRinging(transform: (RingingPrefs) -> RingingPrefs) = viewModelScope.launch { settings.updateRinging(transform) }

    /** Rings the in-app alarm in 5 seconds, so settings can be tried without waiting for the morning. */
    fun testAlarm(): Boolean = internalAlarm.setAt(System.currentTimeMillis() + 5_000, test = true)

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
