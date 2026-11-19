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
import com.ilsecondodasinistra.parakeet.data.PostWakeSession
import com.ilsecondodasinistra.parakeet.data.SettingsRepository
import com.ilsecondodasinistra.parakeet.data.TaskRepository
import com.ilsecondodasinistra.parakeet.domain.PostWakeAction
import com.ilsecondodasinistra.parakeet.domain.PostWakeEvent
import com.ilsecondodasinistra.parakeet.domain.PostWakeTimeline
import com.ilsecondodasinistra.parakeet.domain.Task
import com.ilsecondodasinistra.parakeet.domain.TimelineState
import com.ilsecondodasinistra.parakeet.ui.postwake.PostWakeActivity
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "After the alarm": from Stop to the exit, the day's activities laid end to end ([PostWakeTimeline]).
 * Owns the persisted [PostWakeSession], the ongoing "next thing to do" notification and the AlarmManager
 * wake-ups at the moments the timeline moves on by itself. The ring + list screen reads the same session.
 */
@Singleton
class PostWake @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val tasks: TaskRepository,
    private val alarmSync: AlarmSync,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Fire-and-forget [begin], for callers that are about to be destroyed (the stopping alarm service). */
    fun beginAsync() {
        appScope.launch { begin() }
    }

    /**
     * Starts the morning for the occurrence that just rang. Called when the alarm is stopped by the user
     * (not on snooze, not on auto-stop). Returns false when there is nothing to guide: feature off, Clock
     * mode, fewer than two activities, or the exit already passed.
     */
    suspend fun begin(): Boolean = mutex.withLock {
        settings.clearPostWakeSession() // whatever the outcome below, an older morning must not linger
        val s = settings.current()
        if (!s.postWake.enabled || s.alarmMode != AlarmMode.INTERNAL) return false
        val day = s.plan.lastRungDay ?: return false
        val schedule = alarmSync.dayFor(LocalDate.ofEpochDay(day))
        val wake = schedule.wakeAt ?: return false
        val exit = schedule.exitAt ?: return false
        val steps = tasks.tasks.first().filter { it.id in schedule.taskIds }
        if (steps.size < 2 || exit.toMillis() <= System.currentTimeMillis()) return false
        start(PostWakeSession(day, wake.toMillis(), exit.toMillis(), steps.map { it.id }, PostWakeTimeline.MINUTE_MILLIS, emptyList()))
        true
    }

    /** A rehearsal: the next day's activities (or the checked ones) at 1 minute = 5 seconds, starting now. */
    suspend fun startDemo(): Boolean = mutex.withLock {
        val catalog = tasks.tasks.first()
        val planned = alarmSync.next()?.taskIds.orEmpty()
        var steps = catalog.filter { it.id in planned }
        if (steps.size < 2) steps = catalog.filter { it.checked }
        if (steps.size < 2) steps = catalog
        steps = steps.take(DEMO_MAX_STEPS)
        if (steps.size < 2) return false
        val now = System.currentTimeMillis()
        val exit = now + steps.sumOf { it.minutes } * DEMO_MS_PER_MINUTE
        start(PostWakeSession(LocalDate.now().toEpochDay(), now, exit, steps.map { it.id }, DEMO_MS_PER_MINUTE, emptyList()))
        true
    }

    private suspend fun start(session: PostWakeSession) {
        settings.startPostWakeSession(session)
        refreshLocked()
    }

    /** "Done" / "Do it later" / "Skip for today" on [taskId]. Ignored unless that task is the current one (a stale notification tap). */
    suspend fun act(taskId: Long, action: PostWakeAction) = mutex.withLock {
        val session = settings.current().postWakeSession ?: return@withLock
        val now = System.currentTimeMillis()
        val state = timeline(session, tasks.tasks.first(), now)
        if (state.current?.task?.id != taskId) return@withLock
        if (action == PostWakeAction.DEFER && !state.canDefer) return@withLock // nobody else to do first
        settings.appendPostWakeEvent(PostWakeEvent(taskId, action, now))
        refreshLocked()
    }

    /** Re-reads the session and brings notification and wake-ups in line (boundary reached, boot, time change). */
    suspend fun refresh() = mutex.withLock { refreshLocked() }

    /** Ends the session now (feature switched off). */
    suspend fun clear() = mutex.withLock {
        settings.clearPostWakeSession()
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        alarmManager.cancel(tickIntent())
    }

    private suspend fun refreshLocked() {
        val s = settings.current()
        val session = s.postWakeSession
        val state = session?.let { timeline(it, tasks.tasks.first(), System.currentTimeMillis()) }
        val next = state?.nextBoundaryMillis
        if (session == null || state == null || next == null || !s.postWake.enabled) {
            // Exit passed (the "time to leave" notification takes over) or feature off
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            alarmManager.cancel(tickIntent())
            return
        }
        if (s.postWake.notify && Notifications.canPost(context)) post(state, s.postWake.ring)
        else NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        val pi = tickIntent()
        if (alarmManager.canScheduleExact()) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
    }

    private fun post(state: TimelineState, ring: Boolean) {
        val current = state.current
        val exitTime = Instant.ofEpochMilli(state.exitAtMillis).atZone(ZoneId.systemDefault()).format(TIME)
        val open = PendingIntent.getActivity(
            context, RC_OPEN,
            Intent(context, if (ring) PostWakeActivity::class.java else MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, Notifications.CHANNEL_POST_WAKE)
            .setSmallIcon(R.drawable.ic_stat_parakeet)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
        if (current != null) {
            // The system draws the live clock: down to the end of the planned time, up once the activity runs over
            builder.setContentTitle(current.task.name)
                .setContentText(context.getString(if (current.overrun) R.string.notif_postwake_late else R.string.notif_postwake_text, exitTime))
                .setShowWhen(true).setWhen(current.plannedEndMillis).setUsesChronometer(true).setChronometerCountDown(!current.overrun)
                .addAction(0, context.getString(R.string.postwake_done), action(ACTION_DONE, RC_DONE, current.task.id))
            if (state.canDefer) {
                builder.addAction(0, context.getString(R.string.postwake_later), action(ACTION_LATER, RC_LATER, current.task.id))
            }
            builder.addAction(0, context.getString(R.string.postwake_skip), action(ACTION_SKIP, RC_SKIP, current.task.id))
        } else {
            builder.setContentTitle(context.getString(R.string.notif_postwake_all_done))
                .setContentText(context.getString(R.string.notif_postwake_margin, state.marginMinutes, exitTime))
                .setShowWhen(true).setWhen(state.exitAtMillis).setUsesChronometer(true).setChronometerCountDown(true)
        }
        @Suppress("MissingPermission") // checked by Notifications.canPost
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    private fun action(action: String, requestCode: Int, taskId: Long): PendingIntent = PendingIntent.getBroadcast(
        context, requestCode,
        Intent(context, PostWakeReceiver::class.java).setAction(action).putExtra(EXTRA_TASK, taskId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun tickIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, RC_TICK, Intent(context, PostWakeReceiver::class.java).setAction(ACTION_TICK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun java.time.LocalDateTime.toMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    companion object {
        const val ACTION_TICK = "com.ilsecondodasinistra.parakeet.POSTWAKE_TICK"
        const val ACTION_DONE = "com.ilsecondodasinistra.parakeet.POSTWAKE_DONE"
        const val ACTION_LATER = "com.ilsecondodasinistra.parakeet.POSTWAKE_LATER"
        const val ACTION_SKIP = "com.ilsecondodasinistra.parakeet.POSTWAKE_SKIP"
        const val EXTRA_TASK = "task"
        private const val NOTIFICATION_ID = 20
        private const val RC_TICK = 20
        private const val RC_DONE = 21
        private const val RC_LATER = 22
        private const val RC_OPEN = 23
        private const val RC_SKIP = 24
        private const val DEMO_MS_PER_MINUTE = 5_000L
        private const val DEMO_MAX_STEPS = 5
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

        /** The session resolved against the catalog (edited names show up, deleted activities drop out). */
        fun timeline(session: PostWakeSession, catalog: List<Task>, nowMillis: Long): TimelineState {
            val byId = catalog.associateBy { it.id }
            return PostWakeTimeline.compute(
                session.wakeAtMillis, session.exitAtMillis, session.taskIds.mapNotNull(byId::get),
                session.events, nowMillis, session.msPerMinute,
            )
        }
    }
}

/** Notification buttons ("Done", "Do it later", "Skip for today") and the wake-up at each moment the timeline moves on. */
@AndroidEntryPoint
class PostWakeReceiver : BroadcastReceiver() {
    @Inject lateinit var postWake: PostWake

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val taskId = intent.getLongExtra(PostWake.EXTRA_TASK, -1L)
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    PostWake.ACTION_DONE -> postWake.act(taskId, PostWakeAction.DONE)
                    PostWake.ACTION_LATER -> postWake.act(taskId, PostWakeAction.DEFER)
                    PostWake.ACTION_SKIP -> postWake.act(taskId, PostWakeAction.SKIP)
                    else -> postWake.refresh()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
