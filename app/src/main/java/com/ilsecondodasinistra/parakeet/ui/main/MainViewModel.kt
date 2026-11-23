package com.ilsecondodasinistra.parakeet.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilsecondodasinistra.parakeet.R
import com.ilsecondodasinistra.parakeet.alarm.AlarmScheduler
import com.ilsecondodasinistra.parakeet.alarm.AlarmSync
import com.ilsecondodasinistra.parakeet.alarm.ExitNotificationScheduler
import com.ilsecondodasinistra.parakeet.alarm.InternalAlarmScheduler
import com.ilsecondodasinistra.parakeet.alarm.Notifications
import com.ilsecondodasinistra.parakeet.alarm.PostWake
import com.ilsecondodasinistra.parakeet.data.ActiveAlarm
import com.ilsecondodasinistra.parakeet.data.AlarmMode
import com.ilsecondodasinistra.parakeet.data.LegacyMigration
import com.ilsecondodasinistra.parakeet.data.PlanMode
import com.ilsecondodasinistra.parakeet.data.PlanPrefs
import com.ilsecondodasinistra.parakeet.data.PlanRepository
import com.ilsecondodasinistra.parakeet.data.PostWakePrefs
import com.ilsecondodasinistra.parakeet.data.RingingPrefs
import com.ilsecondodasinistra.parakeet.data.Settings
import com.ilsecondodasinistra.parakeet.data.SettingsRepository
import com.ilsecondodasinistra.parakeet.data.TaskRepository
import com.ilsecondodasinistra.parakeet.domain.AlarmPlanner
import com.ilsecondodasinistra.parakeet.domain.DayPlan
import com.ilsecondodasinistra.parakeet.domain.DaySchedule
import com.ilsecondodasinistra.parakeet.domain.Routine
import com.ilsecondodasinistra.parakeet.domain.Task
import com.ilsecondodasinistra.parakeet.domain.TaskOrder
import com.ilsecondodasinistra.parakeet.domain.WakeUpCalculator
import com.ilsecondodasinistra.parakeet.domain.WakeUpPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/** Alarm sent to the Clock app (Clock mode only). */
data class AlarmStatus(val wakeAt: LocalDateTime, val exitAt: LocalDateTime)

/** The selected day, as the day editor shows it. Editing it creates a one-time plan for that date. */
data class DayUi(
    val date: LocalDate,
    val schedule: DaySchedule,
    val weekly: Routine?,        // routine that owns this weekday (enabled)
    val override: DayPlan?,
    val routine: Routine?,       // routine in use, or the one it would use
    val exitTime: LocalTime?,
    val taskIds: List<Long>,     // the selected activities, in the order of the day
    val paused: Boolean,
    val rung: Boolean,           // its alarm already rang
) {
    val rings: Boolean get() = schedule.rings
    val modified: Boolean get() = override is DayPlan.Custom
}

/** An activity to create from a list of suggestions: the cushion of the plan is flagged, not named. */
data class NewTask(val name: String, val minutes: Int, val buffer: Boolean = false)

data class MainUiState(
    val ready: Boolean = false,
    val showOnboarding: Boolean = false,
    val showAlarmIntro: Boolean = false,
    val now: LocalDateTime = LocalDateTime.now(),
    val tasks: List<Task> = emptyList(),
    val routines: List<Routine> = emptyList(),
    val ribbon: List<DaySchedule> = emptyList(),
    val next: DaySchedule? = null,
    val day: DayUi? = null,
    val pausedUntil: LocalDate? = null,
    val lastRungDay: LocalDate? = null,
    val clockAlarm: AlarmStatus? = null,
    val showDaysHint: Boolean = false,
    /** No "time for the unexpected" in the catalog yet: a discreet suggestion under the list (until it is waved away). */
    val showBufferHint: Boolean = false,
    val notifyOnExit: Boolean = true,
    val exitText: String = "",
    val alarmMode: AlarmMode = AlarmMode.CLOCK,
    val ringing: RingingPrefs = RingingPrefs(),
    val planPrefs: PlanPrefs = PlanPrefs(),
    val planMode: PlanMode = PlanMode.SIMPLE,
    val postWake: PostWakePrefs = PostWakePrefs(),
    /** One-off proposal of "After the alarm": after the second real ring, only if it would have something to show. */
    val showPostWakeHint: Boolean = false,
    /** A morning is in progress (feature on, exit not reached yet): the card that reopens the ring + list. */
    val postWakeActive: Boolean = false,
    // Simple mode: the first routine is "the" setup; the plan is what the "Set alarm" button would arm
    val simpleExit: LocalTime? = null,
    val simpleTaskIds: List<Long> = emptyList(),
    val simplePlan: WakeUpPlan? = null,
)

sealed interface MainEvent {
    /** The alarm moved (or disappeared) after a change: confirmation snackbar. */
    data class NextChanged(val next: DaySchedule?) : MainEvent
    data class AlarmSentToClock(val wakeAt: LocalDateTime) : MainEvent
    data object NoClockApp : MainEvent
    data object NeedsExactAlarm : MainEvent
    data object AlarmCancelled : MainEvent
    data class TaskRemoved(val task: Task) : MainEvent
    /** A day was switched on but there is no routine yet: open the editor for it. */
    data class CreateRoutineFor(val date: LocalDate) : MainEvent
}

private const val RIBBON_DAYS = 56
private const val DEFAULT_BUFFER_MINUTES = 5

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tasks: TaskRepository,
    private val plan: PlanRepository,
    private val settings: SettingsRepository,
    private val migration: LegacyMigration,
    private val alarmScheduler: AlarmScheduler,
    private val internalAlarm: InternalAlarmScheduler,
    private val exitNotifications: ExitNotificationScheduler,
    private val alarmSync: AlarmSync,
    private val postWake: PostWake,
    private val clock: Clock,
) : ViewModel() {

    private val startup = MutableStateFlow(false)
    private val selectedDate = MutableStateFlow<LocalDate?>(null)
    private val _events = MutableSharedFlow<MainEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<MainEvent> = _events.asSharedFlow()

    /** Emits the current time every 15 s: countdown, "today/tomorrow" and the ribbon stay fresh. */
    private val ticker = flow {
        while (true) {
            emit(LocalDateTime.now(clock))
            delay(15_000)
        }
    }

    private data class Data(
        val tasks: List<Task>,
        val routines: List<Routine>,
        val overrides: Map<LocalDate, DayPlan>,
        val settings: Settings,
    )

    private val data = combine(tasks.tasks, plan.routines, plan.overrides, settings.settings, ::Data)

    val state: StateFlow<MainUiState> = combine(data, startup, selectedDate, ticker) { d, ready, selected, now ->
        val s = d.settings
        val today = now.toLocalDate()
        val paused = s.plan.pausedUntil?.let(LocalDate::ofEpochDay)?.takeIf { !it.isBefore(today) }
        val lastRung = s.plan.lastRungDay?.let(LocalDate::ofEpochDay)
        val simple = s.planMode == PlanMode.SIMPLE
        val next = AlarmPlanner.next(
            now,
            if (simple) d.routines.map { it.copy(days = emptySet()) } else d.routines,
            d.overrides, d.tasks,
            if (simple) null else paused,
            if (simple) null else lastRung,
        )
        val simpleRoutine = d.routines.firstOrNull()
        val simpleIds = TaskOrder.normalize(simpleRoutine?.taskIds.orEmpty(), d.tasks)
        val simpleExit = simpleRoutine?.exitTime ?: s.exitTime
        val simplePlan = simpleExit?.let { WakeUpCalculator.plan(it, d.tasks.map { t -> t.copy(checked = t.id in simpleIds) }, now) }
        val ribbon = AlarmPlanner.range(today, RIBBON_DAYS, d.routines, d.overrides, d.tasks, paused)
        val date = selected?.takeIf { !it.isBefore(today) } ?: next?.date ?: today.plusDays(1)
        val clockAlarm = s.activeAlarm
            ?.takeIf { s.alarmMode == AlarmMode.CLOCK && it.exitAtMillis > System.currentTimeMillis() }
            ?.let { AlarmStatus(it.wakeAtMillis.toLocal(), it.exitAtMillis.toLocal()) }
        MainUiState(
            ready = ready,
            showOnboarding = ready && !s.onboardingDone,
            showAlarmIntro = ready && s.onboardingDone && !s.alarmIntroSeen,
            now = now,
            tasks = d.tasks,
            routines = d.routines,
            ribbon = ribbon,
            next = next,
            day = dayUi(date, d, paused, lastRung, now),
            pausedUntil = paused,
            lastRungDay = lastRung,
            clockAlarm = clockAlarm,
            showBufferHint = s.onboardingDone && !s.plan.bufferHintDismissed && d.tasks.isNotEmpty() && d.tasks.none { it.buffer },
            showDaysHint = s.planMode == PlanMode.SCHEDULED && !s.plan.hintDismissed && next != null && d.routines.isNotEmpty() && d.routines.all { it.days.isEmpty() },
            notifyOnExit = s.notifyOnExit,
            exitText = s.exitText,
            alarmMode = s.alarmMode,
            ringing = s.ringing,
            planPrefs = s.plan,
            planMode = s.planMode,
            postWake = s.postWake,
            showPostWakeHint = s.alarmMode == AlarmMode.INTERNAL && s.onboardingDone && !s.postWake.enabled &&
                !s.postWake.hintDismissed && s.postWake.ringCount >= 2 &&
                (next?.taskIds?.count { id -> d.tasks.any { it.id == id && !it.buffer } } ?: 0) >= 2,
            postWakeActive = s.postWake.enabled && s.postWake.ring && (s.postWakeSession?.exitAtMillis ?: 0L) > System.currentTimeMillis(),
            simpleExit = simpleExit,
            simpleTaskIds = simpleIds,
            simplePlan = simplePlan,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch {
            migration.migrateIfNeeded()
            plan.migrateFromSingleSetupIfNeeded(context.getString(R.string.routine_default_name))
            startup.value = true
            alarmSync.sync()
        }
    }

    private fun dayUi(date: LocalDate, d: Data, paused: LocalDate?, lastRung: LocalDate?, now: LocalDateTime): DayUi {
        val schedule = AlarmPlanner.day(date, d.routines, d.overrides, d.tasks, paused)
        val weekly = d.routines.firstOrNull { it.enabled && date.dayOfWeek in it.days }
        val override = d.overrides[date]
        val owner = d.routines.firstOrNull { date.dayOfWeek in it.days }
        val routine = when (override) {
            is DayPlan.UseRoutine -> d.routines.firstOrNull { it.id == override.routineId }
            is DayPlan.Custom -> d.routines.firstOrNull { it.id == override.routineId }
            else -> null
        } ?: weekly ?: owner
        val custom = override as? DayPlan.Custom
        return DayUi(
            date = date,
            schedule = schedule,
            weekly = weekly,
            override = override,
            routine = routine,
            exitTime = custom?.exitTime ?: routine?.exitTime,
            taskIds = TaskOrder.normalize(custom?.taskIds ?: routine?.taskIds.orEmpty(), d.tasks),
            paused = paused != null && !date.isAfter(paused),
            rung = (lastRung != null && !date.isAfter(lastRung)) || (schedule.wakeAt != null && !schedule.wakeAt.isAfter(now)),
        )
    }

    /** Answer to the "new in 5.0" card: try the Parakeet alarm or keep the Clock app. */
    fun answerAlarmIntro(useParakeetAlarm: Boolean) = viewModelScope.launch {
        if (useParakeetAlarm && canUseInternalAlarm()) switchAlarmMode(AlarmMode.INTERNAL)
        else {
            settings.setAlarmIntroSeen()
            if (useParakeetAlarm) _events.emit(MainEvent.NeedsExactAlarm)
        }
    }

    // ---- the selected day (one-time changes) -----------------------------------------------------

    fun selectDate(date: LocalDate) { selectedDate.value = date }

    /** Day switch: off = skip (or remove the extra alarm); on = back to its routine, or an extra alarm. */
    fun setDayOn(on: Boolean) = viewModelScope.launch {
        val day = state.value.day ?: return@launch
        if (on) {
            when {
                day.override is DayPlan.Skip && day.weekly != null -> plan.clearDayPlan(day.date)
                day.weekly != null -> plan.clearDayPlan(day.date)
                else -> {
                    val routine = day.routine ?: state.value.next?.routine ?: state.value.routines.firstOrNull()
                    if (routine == null) { _events.emit(MainEvent.CreateRoutineFor(day.date)); return@launch }
                    plan.setDayPlan(DayPlan.UseRoutine(day.date, routine.id))
                }
            }
        } else {
            if (day.weekly != null) plan.setDayPlan(DayPlan.Skip(day.date)) else plan.clearDayPlan(day.date)
        }
        afterChange()
    }

    /** Routine for the selected day: only that date, or from now on every same weekday ([always]). */
    fun setDayRoutine(routineId: Long, always: Boolean) = viewModelScope.launch {
        val day = state.value.day ?: return@launch
        val routine = state.value.routines.firstOrNull { it.id == routineId } ?: return@launch
        val weekday = day.date.dayOfWeek
        if (always) {
            plan.setDays(routine.id, routine.days + weekday, takeOver = true)
            if (!routine.enabled) plan.setEnabled(routine.copy(days = routine.days + weekday), true)
            plan.clearDayPlan(day.date)
        } else {
            if (weekday in routine.days) plan.setDays(routine.id, routine.days - weekday)
            plan.setDayPlan(DayPlan.UseRoutine(day.date, routine.id))
        }
        afterChange()
    }

    fun setDayExit(time: LocalTime) = viewModelScope.launch {
        val day = state.value.day ?: return@launch
        applyDay(day, time, day.taskIds)
    }

    fun toggleDayTask(task: Task) = viewModelScope.launch {
        val day = state.value.day ?: return@launch
        // A newly ticked activity goes after the last ticked one (before the cushion)
        val ids = if (task.id in day.taskIds) day.taskIds - task.id else TaskOrder.added(day.taskIds, task.id, state.value.tasks)
        applyDay(day, day.exitTime ?: return@launch, ids)
    }

    /** Back to the routine values for that date. */
    fun resetDay() = viewModelScope.launch {
        val day = state.value.day ?: return@launch
        val routine = day.routine
        if (routine == null || day.weekly?.id == routine.id) plan.clearDayPlan(day.date)
        else plan.setDayPlan(DayPlan.UseRoutine(day.date, routine.id))
        afterChange()
    }

    /** "Save in <routine>": the one-time tweak becomes the routine. */
    fun saveDayToRoutine() = viewModelScope.launch {
        val day = state.value.day ?: return@launch
        val routine = day.routine ?: return@launch
        val exit = day.exitTime ?: return@launch
        plan.saveRoutine(routine.copy(exitTime = exit, taskIds = day.taskIds))
        if (day.weekly?.id == routine.id) plan.clearDayPlan(day.date) else plan.setDayPlan(DayPlan.UseRoutine(day.date, routine.id))
        afterChange()
    }

    private suspend fun applyDay(day: DayUi, exit: LocalTime, taskIds: List<Long>) {
        val routine = day.routine
        val catalog = state.value.tasks
        // The same activities in the same order: not a change of the day (the order counts)
        if (routine != null && routine.exitTime == exit && TaskOrder.normalize(routine.taskIds, catalog) == TaskOrder.normalize(taskIds, catalog)) {
            if (day.weekly?.id == routine.id) plan.clearDayPlan(day.date) else plan.setDayPlan(DayPlan.UseRoutine(day.date, routine.id))
        } else {
            plan.setDayPlan(DayPlan.Custom(day.date, routine?.id, exit, taskIds))
        }
        afterChange()
    }

    // ---- simple mode ---------------------------------------------------------------------------------

    fun setPlanMode(mode: PlanMode) = viewModelScope.launch {
        settings.setPlanMode(mode)
        alarmSync.sync() // simple mode ignores the weekly days, scheduled mode brings them back
    }

    /** The routine that holds the simple-mode setup (the first one); created when there is none. */
    private suspend fun simpleRoutine(): Routine {
        plan.currentRoutines().firstOrNull()?.let { return it }
        val id = plan.saveRoutine(
            Routine(
                name = context.getString(R.string.routine_default_name), color = plan.nextColor(),
                exitTime = settings.current().exitTime ?: LocalTime.of(8, 0), taskIds = emptyList(),
            ),
        )
        return plan.currentRoutines().first { it.id == id }
    }

    fun toggleSimpleTask(task: Task) = viewModelScope.launch {
        val r = simpleRoutine()
        plan.saveRoutine(r.copy(taskIds = if (task.id in r.taskIds) r.taskIds - task.id else TaskOrder.added(r.taskIds, task.id, state.value.tasks)))
        alarmSync.sync()
    }

    // ---- reorder -------------------------------------------------------------------------------------

    /**
     * Ends the "reorder" of the home list in simple mode: [order] is the new order of the ticked activities.
     */
    fun reorderSimple(order: List<Long>) = viewModelScope.launch {
        val r = simpleRoutine()
        plan.saveRoutine(r.copy(taskIds = TaskOrder.normalize(order, state.value.tasks)))
        alarmSync.sync()
    }

    /**
     * Ends the "reorder" of the selected day: [order] is the new order of its ticked activities. With [allDays] the order
     * becomes the one of the routine (every day that follows it); otherwise it is a one-time change of this date.
     */
    fun reorderDay(order: List<Long>, allDays: Boolean) = viewModelScope.launch {
        val day = state.value.day ?: return@launch
        val ids = TaskOrder.normalize(order, state.value.tasks)
        val routine = day.routine
        if (allDays && routine != null && !day.modified) {
            plan.saveRoutine(routine.copy(taskIds = ids))
            afterChange()
        } else {
            applyDay(day, day.exitTime ?: return@launch, ids)
        }
    }

    fun setSimpleExit(time: LocalTime) = viewModelScope.launch {
        val r = simpleRoutine()
        plan.saveRoutine(r.copy(exitTime = time))
        settings.setExitTime(time)
        alarmSync.sync()
    }

    /** "Set alarm": arms the next wake-up once, replacing any other one-time alarm (one alarm at a time). */
    fun setSimpleAlarm() = viewModelScope.launch {
        val sp = state.value.simplePlan ?: return@launch
        if (sp.totalMinutes == 0) return@launch
        if (settings.current().alarmMode == AlarmMode.CLOCK) {
            if (settings.current().activeAlarm != null) alarmScheduler.cancel()
            if (!alarmScheduler.set(sp.wakeAt)) { _events.emit(MainEvent.NoClockApp); return@launch }
            settings.setActiveAlarm(ActiveAlarm(sp.wakeAt.toMillis(), sp.exitAt.toMillis()))
            if (settings.current().notifyOnExit) exitNotifications.schedule(sp.exitAt.toMillis()) else exitNotifications.cancel()
            _events.emit(MainEvent.AlarmSentToClock(sp.wakeAt))
            return@launch
        }
        if (!canUseInternalAlarm()) { _events.emit(MainEvent.NeedsExactAlarm); return@launch }
        val routine = simpleRoutine()
        val date = sp.exitAt.toLocalDate()
        plan.currentOverrides().values.filter { it.date != date && it !is DayPlan.Skip }.forEach { plan.clearDayPlan(it.date) }
        plan.setDayPlan(DayPlan.UseRoutine(date, routine.id))
        _events.emit(MainEvent.NextChanged(alarmSync.sync()))
    }

    fun cancelSimpleAlarm() = viewModelScope.launch {
        val next = state.value.next ?: return@launch
        plan.clearDayPlan(next.date)
        alarmSync.sync()
        _events.emit(MainEvent.AlarmCancelled)
    }

    // ---- routines --------------------------------------------------------------------------------

    /** Edit of an activity made in the routine editor, applied when the routine is saved. */
    data class TaskEdit(val taskId: Long, val name: String, val minutes: Int, val onlyHere: Boolean)

    /**
     * Saves [routine]; when created for [forDate] and not covering its weekday, it is used on that date only.
     * [edits]: "everywhere" ones change the shared activity; "only here" ones give the routine its own copy of it
     * (the other routines keep the original). Calls [onSaved] with the saved routine once it is stored.
     */
    fun saveRoutine(
        routine: Routine,
        forDate: LocalDate?,
        edits: List<TaskEdit> = emptyList(),
        disable: List<Long> = emptyList(),
        onSaved: (Routine) -> Unit = {},
    ) = viewModelScope.launch {
        // Routines the user agreed to switch off because they covered days this one needs
        state.value.routines.filter { it.id in disable }.forEach { plan.setEnabled(it, false) }
        var taskIds = routine.taskIds
        edits.forEach { e ->
            val task = state.value.tasks.firstOrNull { it.id == e.taskId } ?: return@forEach
            if (e.onlyHere) {
                // The copy takes the place of the original, where it was in the routine
                if (e.taskId in taskIds) { val copy = tasks.add(e.name, e.minutes, buffer = task.buffer); taskIds = taskIds.map { if (it == e.taskId) copy else it } }
            } else {
                tasks.update(task.copy(name = e.name, minutes = e.minutes))
            }
        }
        val toSave = routine.copy(taskIds = taskIds)
        val id = plan.saveRoutine(toSave)
        // One-time "use this routine" plans on days it now covers every week are redundant
        if (routine.enabled) {
            plan.currentOverrides().values
                .filter { it is DayPlan.UseRoutine && it.routineId == id && it.date.dayOfWeek in routine.days }
                .forEach { plan.clearDayPlan(it.date) }
        }
        if (forDate != null) {
            if (forDate.dayOfWeek in routine.days && routine.enabled) plan.clearDayPlan(forDate)
            else plan.setDayPlan(DayPlan.UseRoutine(forDate, id))
            selectedDate.value = forDate
        }
        afterChange()
        onSaved(toSave.copy(id = id))
    }

    /** New routine from [routine]: same exit time and activities, a new colour, no days; [onCreated] gets its id. */
    fun duplicateRoutine(routine: Routine, onCreated: (Long) -> Unit) = viewModelScope.launch {
        val copy = routine.copy(
            id = 0,
            name = context.getString(R.string.routine_copy_name, routine.name),
            color = plan.nextColor(),
            days = emptySet(),
            enabled = true,
        )
        val id = plan.saveRoutine(copy)
        afterChange()
        onCreated(id)
    }

    /** Switches [routine]; [alsoDisable] are routines the user agreed to switch off first (they covered its days). */
    fun setRoutineEnabled(routine: Routine, enabled: Boolean, alsoDisable: List<Routine> = emptyList()) = viewModelScope.launch {
        alsoDisable.forEach { plan.setEnabled(it, false) }
        plan.setEnabled(routine, enabled)
        afterChange()
    }

    fun deleteRoutine(routine: Routine) = viewModelScope.launch {
        plan.deleteRoutine(routine.id)
        afterChange()
    }

    suspend fun nextRoutineColor(): Int = plan.nextColor()

    fun dismissDaysHint() = viewModelScope.launch { settings.setHintDismissed() }

    // ---- after the alarm (5.2) -----------------------------------------------------------------------

    fun setPostWakeEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.updatePostWake { it.copy(enabled = enabled) }
        if (!enabled) postWake.clear()
    }

    fun updatePostWake(transform: (PostWakePrefs) -> PostWakePrefs) = viewModelScope.launch {
        settings.updatePostWake(transform)
        postWake.refresh() // e.g. notification switched off while a morning is in progress
    }

    /** A rehearsal at 1 minute = 5 seconds. [onResult] is false when the plan has fewer than two activities. */
    fun tryPostWake(onResult: (Boolean) -> Unit) = viewModelScope.launch { onResult(postWake.startDemo()) }

    /** "Try it" on the proposal card: switch everything on, then rehearse. */
    fun acceptPostWakeHint(onResult: (Boolean) -> Unit) = viewModelScope.launch {
        settings.updatePostWake { it.copy(enabled = true, ring = true, notify = true, lockScreen = true) }
        onResult(postWake.startDemo())
    }

    fun dismissPostWakeHint() = viewModelScope.launch { settings.setPostWakeHintDismissed() }

    // ---- pause -------------------------------------------------------------------------------------

    fun pauseUntil(date: LocalDate) = viewModelScope.launch {
        settings.setPausedUntil(date.toEpochDay())
        afterChange()
    }

    fun resume() = viewModelScope.launch {
        settings.setPausedUntil(null)
        afterChange()
    }

    // ---- activity catalog ----------------------------------------------------------------------------

    /** New activity: added to the catalog and ticked for the selected day only. */
    fun addTask(name: String, minutes: Int, buffer: Boolean = false) = viewModelScope.launch {
        val id = tasks.add(name, minutes, buffer = buffer)
        if (state.value.planMode == PlanMode.SIMPLE) {
            val r = simpleRoutine()
            plan.saveRoutine(r.copy(taskIds = TaskOrder.added(r.taskIds, id, state.value.tasks + Task(id, name, minutes, true, buffer = buffer))))
            alarmSync.sync()
            return@launch
        }
        val day = state.value.day
        if (day?.exitTime != null && day.rings) applyDay(day, day.exitTime, TaskOrder.added(day.taskIds, id, state.value.tasks + Task(id, name, minutes, true, buffer = buffer)))
    }

    fun updateTask(task: Task) = viewModelScope.launch { tasks.update(task); afterChange() }

    fun removeTask(task: Task) = viewModelScope.launch {
        tasks.delete(task.id)
        _events.emit(MainEvent.TaskRemoved(task))
        afterChange()
    }

    fun restoreTask(task: Task) = viewModelScope.launch { tasks.restore(task); afterChange() }

    /** Adds an activity from the routine editor; returns its id through [onAdded]. */
    fun addCatalogTask(name: String, minutes: Int, onAdded: (Long) -> Unit) = viewModelScope.launch {
        onAdded(tasks.add(name, minutes))
    }

    /** "Time for the unexpected", 5 minutes: from the suggestion under the list. */
    fun addBuffer() = addTask(context.getString(R.string.buffer_task_name), DEFAULT_BUFFER_MINUTES, buffer = true)

    /** The same, from the routine editor, which then selects it. */
    fun addCatalogBuffer(onAdded: (Long) -> Unit) = viewModelScope.launch {
        onAdded(tasks.add(context.getString(R.string.buffer_task_name), DEFAULT_BUFFER_MINUTES, buffer = true))
    }

    fun dismissBufferHint() = viewModelScope.launch { settings.setBufferHintDismissed() }

    private suspend fun afterChange() {
        val next = alarmSync.sync()
        if (settings.current().alarmMode == AlarmMode.INTERNAL) _events.emit(MainEvent.NextChanged(next))
    }

    // ---- Clock app mode --------------------------------------------------------------------------------

    /** Clock mode: sends the next planned alarm to the Clock app. */
    fun sendToClock() = viewModelScope.launch {
        val next = state.value.next ?: return@launch
        val wake = next.wakeAt ?: return@launch
        val exit = next.exitAt ?: return@launch
        if (settings.current().activeAlarm != null) alarmScheduler.cancel()
        if (!alarmScheduler.set(wake)) {
            _events.emit(if (settings.current().alarmMode == AlarmMode.INTERNAL) MainEvent.NeedsExactAlarm else MainEvent.NoClockApp)
            return@launch
        }
        settings.setActiveAlarm(ActiveAlarm(wake.toMillis(), exit.toMillis()))
        if (settings.current().notifyOnExit) exitNotifications.schedule(exit.toMillis()) else exitNotifications.cancel()
        _events.emit(MainEvent.AlarmSentToClock(wake))
    }

    fun cancelClockAlarm() = viewModelScope.launch {
        alarmScheduler.cancel()
        exitNotifications.cancel()
        settings.setActiveAlarm(null)
        _events.emit(MainEvent.AlarmCancelled)
    }

    // ---- onboarding & settings -------------------------------------------------------------------------

    /**
     * Creates the activities and the first routine. No [days] = "only tomorrow": a one-time plan for the
     * next occurrence, so nothing rings on days the user never chose.
     */
    fun completeOnboarding(
        exitTime: LocalTime,
        chosen: List<NewTask>,
        scheduled: Boolean,
        days: Set<DayOfWeek>,
        routineName: String,
        notify: Boolean,
        internalAlarm: Boolean,
    ) = viewModelScope.launch {
        settings.setAlarmMode(if (internalAlarm && canUseInternalAlarm()) AlarmMode.INTERNAL else AlarmMode.CLOCK)
        settings.setExitTime(exitTime)
        val ids = if (tasks.count() == 0) chosen.map { tasks.add(it.name, it.minutes, buffer = it.buffer, seed = true) }
        else tasks.tasks.first().filter { it.checked }.map { it.id }
        val id = plan.saveRoutine(
            Routine(name = routineName, color = plan.nextColor(), exitTime = exitTime, taskIds = ids, days = days),
            seed = true,
        )
        settings.setPlanMode(if (scheduled) PlanMode.SCHEDULED else PlanMode.SIMPLE)
        // Simple mode sets no alarm by itself: the user presses "Set alarm", as before
        if (scheduled && days.isEmpty()) {
            val total = chosen.sumOf { it.minutes }
            val first = WakeUpCalculator.plan(exitTime, listOf(Task(name = "", minutes = total, checked = true)), LocalDateTime.now(clock))
            plan.setDayPlan(DayPlan.UseRoutine(first.exitAt.toLocalDate(), id), seed = true)
        }
        settings.markPlanMigrated()
        settings.setNotifyOnExit(notify)
        settings.setOnboardingDone()
        alarmSync.sync()
    }

    fun setNotifyOnExit(enabled: Boolean) = viewModelScope.launch {
        settings.setNotifyOnExit(enabled)
        if (settings.current().alarmMode == AlarmMode.INTERNAL) { alarmSync.sync(); return@launch }
        val alarm = settings.current().activeAlarm
        if (!enabled) exitNotifications.cancel()
        else if (alarm != null) exitNotifications.schedule(alarm.exitAtMillis)
    }

    fun setExitText(text: String) = viewModelScope.launch { settings.setExitText(text) }

    fun setPreAlarmNotify(enabled: Boolean) = viewModelScope.launch { settings.setPreAlarmNotify(enabled); alarmSync.sync() }

    fun setPreAlarmMinutes(minutes: Int) = viewModelScope.launch { settings.setPreAlarmMinutes(minutes); alarmSync.sync() }

    fun setEveningSummary(enabled: Boolean) = viewModelScope.launch { settings.setEveningSummary(enabled); alarmSync.sync() }

    fun setEveningTime(time: LocalTime) = viewModelScope.launch {
        settings.setEveningMinute(time.hour * 60 + time.minute); alarmSync.sync()
    }

    fun canScheduleExact(): Boolean = exitNotifications.canScheduleExact()

    fun canScheduleExactAlarms(): Boolean = exitNotifications.canScheduleExact()

    /**
     * The in-app alarm is allowed only with exact alarms AND notifications: without notifications it would ring
     * with no visible way to stop it (no full-screen intent, no notification).
     */
    fun canUseInternalAlarm(): Boolean = canScheduleExactAlarms() && Notifications.canPost(context)

    fun setAlarmMode(mode: AlarmMode) = viewModelScope.launch { switchAlarmMode(mode) }

    /** Switches between Clock app and in-app alarm. The in-app alarm follows the weekly plan by itself. */
    private suspend fun switchAlarmMode(mode: AlarmMode) {
        if (mode == AlarmMode.INTERNAL && !canUseInternalAlarm()) return
        val s = settings.current()
        if (s.alarmMode == AlarmMode.CLOCK && mode == AlarmMode.INTERNAL && s.activeAlarm != null) {
            alarmScheduler.cancel() // remove the Clock app alarm we created
            settings.setActiveAlarm(null)
        }
        settings.setAlarmMode(mode)
        alarmSync.sync()
    }

    fun updateRinging(transform: (RingingPrefs) -> RingingPrefs) = viewModelScope.launch { settings.updateRinging(transform) }

    /** Rings the in-app alarm in 5 seconds, so settings can be tried without waiting for the morning. */
    fun testAlarm(): Boolean = internalAlarm.setAt(System.currentTimeMillis() + 5_000, test = true)

    /** Called when exact-alarm access may have changed, to re-arm the notifications with the right precision. */
    fun refreshExitNotification() = viewModelScope.launch {
        val s = settings.current()
        if (s.alarmMode == AlarmMode.INTERNAL) { alarmSync.sync(); return@launch }
        val alarm = s.activeAlarm
        if (s.notifyOnExit && alarm != null) exitNotifications.schedule(alarm.exitAtMillis)
    }

    private fun Long.toLocal(): LocalDateTime =
        java.time.Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

    private fun LocalDateTime.toMillis(): Long =
        atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
