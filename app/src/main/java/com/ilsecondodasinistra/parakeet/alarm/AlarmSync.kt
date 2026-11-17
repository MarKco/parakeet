package com.ilsecondodasinistra.parakeet.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ilsecondodasinistra.parakeet.MainActivity
import com.ilsecondodasinistra.parakeet.R
import com.ilsecondodasinistra.parakeet.data.AlarmMode
import com.ilsecondodasinistra.parakeet.data.PlanMode
import com.ilsecondodasinistra.parakeet.data.PlanRepository
import com.ilsecondodasinistra.parakeet.data.SettingsRepository
import com.ilsecondodasinistra.parakeet.data.TaskRepository
import com.ilsecondodasinistra.parakeet.domain.AlarmPlanner
import com.ilsecondodasinistra.parakeet.domain.DayPlan
import com.ilsecondodasinistra.parakeet.domain.DaySchedule
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth → AlarmManager. Computes the next occurrence of the weekly plan and arms only that
 * one (in-app mode), plus the exit, "upcoming alarm" and evening notifications. Runs on every data change,
 * after each ring, after boot/time changes and when a notification action skips a day.
 */
@Singleton
class AlarmSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val plan: PlanRepository,
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val internal: InternalAlarmScheduler,
    private val exitNotifications: ExitNotificationScheduler,
) {
    private val mutex = Mutex()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Keeps the alarm in step with the data for as long as the process lives. */
    @OptIn(FlowPreview::class)
    fun start() {
        combine(
            plan.routines, plan.overrides, tasks.tasks,
            settings.settings.map { listOf(it.alarmMode, it.notifyOnExit, it.plan, it.planMode) }.distinctUntilChanged(),
        ) { a, b, c, d -> listOf(a, b, c, d) }
            .debounce(250)
            .onEach { sync() }
            .launchIn(appScope)
    }

    /** What would ring next, without arming anything. */
    suspend fun next(now: LocalDateTime = LocalDateTime.now()): DaySchedule? {
        val s = settings.current()
        val simple = s.planMode == PlanMode.SIMPLE
        // Simple mode: only one-time alarms; weekly days, pause and the "already rang" guard do not apply
        return AlarmPlanner.next(
            now, routinesFor(simple), plan.currentOverrides(), tasks.tasks.first(),
            if (simple) null else s.plan.pausedUntil?.let(LocalDate::ofEpochDay),
            if (simple) null else s.plan.lastRungDay?.let(LocalDate::ofEpochDay),
        )
    }

    /** Routines as the plan sees them: in simple mode without their weekdays (kept in the database). */
    private suspend fun routinesFor(simple: Boolean) =
        plan.currentRoutines().let { list -> if (simple) list.map { it.copy(days = emptySet()) } else list }

    suspend fun sync(): DaySchedule? = mutex.withLock {
        // An update from 5.0 may first run here (boot, time change): convert its setup before computing
        plan.migrateFromSingleSetupIfNeeded(context.getString(R.string.routine_default_name))
        val now = LocalDateTime.now()
        plan.prune(now.toLocalDate())
        val s = settings.current()
        val next = next(now)

        if (s.alarmMode != AlarmMode.INTERNAL) {
            // Clock app mode: the user sends the alarm to the Clock app; nothing is armed here
            internal.cancel()
            settings.setArmedDay(null)
            cancel(ACTION_PRE_ALARM); cancel(ACTION_EVENING)
            return@withLock next
        }

        if (next?.wakeAt != null && internal.set(next.wakeAt)) settings.setArmedDay(next.date.toEpochDay())
        else { internal.cancel(); settings.setArmedDay(null) }

        // Exit notification: today's exit (alarm already rung) comes before the next occurrence's exit
        val rungExit = s.plan.lastRungDay?.let { day ->
            AlarmPlanner.day(LocalDate.ofEpochDay(day), routinesFor(s.planMode == PlanMode.SIMPLE), plan.currentOverrides(), tasks.tasks.first()).exitAt
        }
        val exit = listOfNotNull(rungExit, next?.exitAt).map { it.toMillis() }.filter { it > System.currentTimeMillis() }.minOrNull()
        if (s.notifyOnExit && exit != null) exitNotifications.schedule(exit) else exitNotifications.cancel()

        // "Upcoming alarm · Skip" a chosen time before (2 h by default); a notification already shown for another day is stale
        val nowMillis = System.currentTimeMillis()
        val preAlarmMillis = s.plan.preAlarmMinutes * 60_000L
        if (next == null || next.wakeAt!!.toMillis() - nowMillis > preAlarmMillis) {
            NotificationManagerCompat.from(context).cancel(NOTIF_PRE_ALARM)
        }
        if (s.plan.preAlarmNotify && next?.wakeAt != null) {
            val at = next.wakeAt.toMillis() - preAlarmMillis
            if (at > nowMillis) schedule(ACTION_PRE_ALARM, at, next.date) else cancel(ACTION_PRE_ALARM)
        } else cancel(ACTION_PRE_ALARM)

        // Evening summary the day before the alarm
        if (s.plan.eveningSummary && next?.wakeAt != null) {
            val m = s.plan.eveningMinute
            val at = next.date.minusDays(1).atTime(LocalTime.of(m / 60, m % 60)).toMillis()
            if (at > nowMillis && at < next.wakeAt.toMillis()) schedule(ACTION_EVENING, at, next.date) else cancel(ACTION_EVENING)
        } else cancel(ACTION_EVENING)
        next
    }

    /** Called by [AlarmReceiver] when the armed wake-up rings: that date is done, arm the following one. */
    suspend fun onRang() {
        settings.current().plan.armedDay?.let { settings.setLastRungDay(it) }
        NotificationManagerCompat.from(context).cancel(NOTIF_PRE_ALARM)
        sync()
    }

    /** Skips [date] only (notification action). */
    suspend fun skip(date: LocalDate) {
        plan.setDayPlan(DayPlan.Skip(date))
        NotificationManagerCompat.from(context).cancel(NOTIF_PRE_ALARM)
        NotificationManagerCompat.from(context).cancel(NOTIF_EVENING)
        sync()
    }

    fun launch(block: suspend AlarmSync.() -> Unit) = appScope.launch { block() }

    private fun schedule(action: String, atMillis: Long, date: LocalDate) {
        val pi = pending(action, date)
        if (alarmManager.canScheduleExactAlarms()) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
    }

    private fun cancel(action: String) = alarmManager.cancel(pending(action, null))

    private fun pending(action: String, date: LocalDate?): PendingIntent = PendingIntent.getBroadcast(
        context,
        if (action == ACTION_PRE_ALARM) RC_PRE_ALARM else RC_EVENING,
        Intent(context, PlanReceiver::class.java).setAction(action).putExtra(EXTRA_DAY, date?.toEpochDay() ?: -1L),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Posts the "upcoming alarm" or evening notification, if [day] is still the next alarm. */
    internal suspend fun notifyUpcoming(action: String, day: Long) {
        val next = next() ?: return
        if (next.date.toEpochDay() != day || next.wakeAt == null || !Notifications.canPost(context)) return
        val wake = next.wakeAt.format(TIME)
        val exit = next.exitAt?.format(TIME).orEmpty()
        val routine = next.routine?.name.orEmpty()
        val evening = action == ACTION_EVENING
        val dayName = next.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM"))
        val title = if (evening) context.getString(R.string.notif_evening_title, wake) else context.getString(R.string.notif_upcoming_title, wake)
        val text = if (routine.isNotEmpty()) context.getString(R.string.notif_upcoming_text, routine, exit)
        else context.getString(R.string.notif_upcoming_text_plain, exit)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val skip = PendingIntent.getBroadcast(
            context, RC_SKIP,
            Intent(context, PlanReceiver::class.java).setAction(ACTION_SKIP).putExtra(EXTRA_DAY, day),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_UPCOMING)
            .setSmallIcon(R.drawable.ic_stat_parakeet)
            .setContentTitle(title)
            .setContentText(if (evening) "$dayName · $text" else text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, context.getString(if (evening) R.string.notif_skip_tomorrow else R.string.notif_skip_this), skip)
            .build()
        @Suppress("MissingPermission") // checked by Notifications.canPost
        NotificationManagerCompat.from(context).notify(if (evening) NOTIF_EVENING else NOTIF_PRE_ALARM, notification)
    }

    private fun LocalDateTime.toMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    companion object {
        const val ACTION_PRE_ALARM = "com.ilsecondodasinistra.parakeet.PRE_ALARM"
        const val ACTION_EVENING = "com.ilsecondodasinistra.parakeet.EVENING"
        const val ACTION_SKIP = "com.ilsecondodasinistra.parakeet.SKIP_DAY"
        const val EXTRA_DAY = "day"
        private const val RC_PRE_ALARM = 10
        private const val RC_EVENING = 11
        private const val RC_SKIP = 12
        private const val NOTIF_PRE_ALARM = 10
        private const val NOTIF_EVENING = 11
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    }
}

/** Upcoming / evening notifications and their "Skip" action. */
@AndroidEntryPoint
class PlanReceiver : BroadcastReceiver() {
    @Inject lateinit var sync: AlarmSync

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val day = intent.getLongExtra(AlarmSync.EXTRA_DAY, -1L)
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    AlarmSync.ACTION_SKIP -> if (day >= 0) sync.skip(LocalDate.ofEpochDay(day))
                    AlarmSync.ACTION_PRE_ALARM, AlarmSync.ACTION_EVENING -> sync.notifyUpcoming(intent.action!!, day)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
