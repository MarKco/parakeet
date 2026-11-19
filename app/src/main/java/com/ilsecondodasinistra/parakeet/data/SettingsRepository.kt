package com.ilsecondodasinistra.parakeet.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ilsecondodasinistra.parakeet.domain.PostWakeEvent
import com.ilsecondodasinistra.parakeet.domain.PostWakeTimeline
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Alarm already set through the Clock app, kept to show its status and to allow cancelling. */
data class ActiveAlarm(val wakeAtMillis: Long, val exitAtMillis: Long)

enum class AlarmMode { CLOCK, INTERNAL }

/**
 * SIMPLE: wake-up time and selectable activities, one alarm at a time (the pre-5.1 screen). The weekly
 * days of the routines are ignored, not deleted. SCHEDULED: routines per weekday and one-time changes.
 */
enum class PlanMode { SIMPLE, SCHEDULED }

/** How the ringing alarm is switched off. */
enum class Challenge { SLIDE, MATH, SHAKE, QR }

/** Options of the in-app ringing alarm. [snoozeMinutes] 0 = snooze disabled. [ringtone] empty = system default alarm. */
data class RingingPrefs(
    val ringtone: String = "",
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 10,
    val challenge: Challenge = Challenge.SLIDE,
    val mathOps: Int = 3,
    val shakeCount: Int = 20,
    val qrValue: String = "",
)

/** Weekly-plan state kept outside Room. Dates as epoch days. */
data class PlanPrefs(
    val pausedUntil: Long? = null,       // alarms suspended up to this date (inclusive)
    val lastRungDay: Long? = null,       // date of the last occurrence that rang: never re-armed
    val armedDay: Long? = null,          // date of the occurrence currently armed
    val preAlarmNotify: Boolean = true,  // "alarm at 6:45 · Skip" some time before
    val preAlarmMinutes: Int = 120,      // how long before the alarm
    val eveningSummary: Boolean = false, // "tomorrow: alarm 6:45" the evening before
    val eveningMinute: Int = 21 * 60 + 30,
    val hintDismissed: Boolean = false,  // "ring on other days too?" hint
    val bufferHintDismissed: Boolean = false, // "add time for the unexpected?" suggestion
)

/**
 * "After the alarm" (5.2). Off until the user opts in. [ring] = the ring + list screen, [notify] = the
 * "next thing to do" notification, [lockScreen] = open the screen over the lock screen right after Stop
 * (needs [ring]). [ringCount] counts real rings, to offer the feature once after the second one.
 */
data class PostWakePrefs(
    val enabled: Boolean = false,
    val ring: Boolean = true,
    val notify: Boolean = true,
    val lockScreen: Boolean = true,
    val ringCount: Int = 0,
    val hintDismissed: Boolean = false,
)

/**
 * The morning in progress: snapshot taken at Stop, so editing a routine afterwards doesn't move the
 * timeline. [msPerMinute] is 60 000 except in the demo.
 */
data class PostWakeSession(
    val day: Long,
    val wakeAtMillis: Long,
    val exitAtMillis: Long,
    val taskIds: List<Long>,
    val msPerMinute: Long,
    val events: List<PostWakeEvent>,
    /** The "you are running late" alert was already shown this morning: it comes once. */
    val lateAlerted: Boolean = false,
)

data class Settings(
    val exitTime: LocalTime?,           // null until the user chooses one
    val onboardingDone: Boolean,
    val notifyOnExit: Boolean,
    val exitText: String,               // empty = default text
    val activeAlarm: ActiveAlarm?,
    val alarmMode: AlarmMode,           // CLOCK until the user opts in to the in-app alarm
    val alarmIntroSeen: Boolean,        // the "new in 5.0" card was answered
    val ringing: RingingPrefs,
    val plan: PlanPrefs = PlanPrefs(),
    val planMode: PlanMode = PlanMode.SIMPLE, // upgraders keep the screen they know
    val postWake: PostWakePrefs = PostWakePrefs(),
    val postWakeSession: PostWakeSession? = null,
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val exitMinuteOfDay = intPreferencesKey("exit_minute_of_day")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val notifyOnExit = booleanPreferencesKey("notify_on_exit")
        val exitText = stringPreferencesKey("exit_text")
        val alarmWakeAt = longPreferencesKey("alarm_wake_at")
        val alarmExitAt = longPreferencesKey("alarm_exit_at")
        val legacyMigrated = booleanPreferencesKey("legacy_migrated")
        val lastSeenVersion = intPreferencesKey("last_seen_version")
        val alarmMode = stringPreferencesKey("alarm_mode")
        val alarmIntroSeen = booleanPreferencesKey("alarm_intro_seen")
        val ringtone = stringPreferencesKey("ringtone")
        val vibrate = booleanPreferencesKey("vibrate")
        val snoozeMinutes = intPreferencesKey("snooze_minutes")
        val challenge = stringPreferencesKey("challenge")
        val mathOps = intPreferencesKey("math_ops")
        val shakeCount = intPreferencesKey("shake_count")
        val qrValue = stringPreferencesKey("qr_value")
        val pausedUntil = longPreferencesKey("paused_until")
        val lastRungDay = longPreferencesKey("last_rung_day")
        val armedDay = longPreferencesKey("armed_day")
        val preAlarmNotify = booleanPreferencesKey("pre_alarm_notify")
        val preAlarmMinutes = intPreferencesKey("pre_alarm_minutes")
        val eveningSummary = booleanPreferencesKey("evening_summary")
        val eveningMinute = intPreferencesKey("evening_minute")
        val hintDismissed = booleanPreferencesKey("plan_hint_dismissed")
        val bufferHintDismissed = booleanPreferencesKey("buffer_hint_dismissed")
        val planMigrated = booleanPreferencesKey("plan_migrated")
        val planMode = stringPreferencesKey("plan_mode")
        val pwEnabled = booleanPreferencesKey("postwake_enabled")
        val pwRing = booleanPreferencesKey("postwake_ring")
        val pwNotify = booleanPreferencesKey("postwake_notify")
        val pwLockScreen = booleanPreferencesKey("postwake_lock_screen")
        val pwRingCount = intPreferencesKey("postwake_ring_count")
        val pwHintDismissed = booleanPreferencesKey("postwake_hint_dismissed")
        val pwDay = longPreferencesKey("postwake_day")
        val pwWake = longPreferencesKey("postwake_wake_at")
        val pwExit = longPreferencesKey("postwake_exit_at")
        val pwTaskIds = stringPreferencesKey("postwake_task_ids")
        val pwScale = longPreferencesKey("postwake_ms_per_minute")
        val pwEvents = stringPreferencesKey("postwake_events")
        val pwLateAlerted = booleanPreferencesKey("postwake_late_alerted")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    private fun Preferences.toSettings(): Settings {
        val wake = this[Keys.alarmWakeAt]
        val exit = this[Keys.alarmExitAt]
        return Settings(
            exitTime = this[Keys.exitMinuteOfDay]?.let { LocalTime.of(it / 60, it % 60) },
            onboardingDone = this[Keys.onboardingDone] ?: false,
            notifyOnExit = this[Keys.notifyOnExit] ?: true,
            exitText = this[Keys.exitText].orEmpty(),
            activeAlarm = if (wake != null && exit != null) ActiveAlarm(wake, exit) else null,
            alarmMode = this[Keys.alarmMode]?.let { runCatching { AlarmMode.valueOf(it) }.getOrNull() } ?: AlarmMode.CLOCK,
            alarmIntroSeen = this[Keys.alarmIntroSeen] ?: false,
            ringing = RingingPrefs(
                ringtone = this[Keys.ringtone].orEmpty(),
                vibrate = this[Keys.vibrate] ?: true,
                snoozeMinutes = this[Keys.snoozeMinutes] ?: 10,
                challenge = this[Keys.challenge]?.let { runCatching { Challenge.valueOf(it) }.getOrNull() } ?: Challenge.SLIDE,
                mathOps = this[Keys.mathOps] ?: 3,
                shakeCount = this[Keys.shakeCount] ?: 20,
                qrValue = this[Keys.qrValue].orEmpty(),
            ),
            plan = PlanPrefs(
                pausedUntil = this[Keys.pausedUntil],
                lastRungDay = this[Keys.lastRungDay],
                armedDay = this[Keys.armedDay],
                preAlarmNotify = this[Keys.preAlarmNotify] ?: true,
                preAlarmMinutes = this[Keys.preAlarmMinutes] ?: 120,
                eveningSummary = this[Keys.eveningSummary] ?: false,
                eveningMinute = this[Keys.eveningMinute] ?: (21 * 60 + 30),
                hintDismissed = this[Keys.hintDismissed] ?: false,
                bufferHintDismissed = this[Keys.bufferHintDismissed] ?: false,
            ),
            planMode = this[Keys.planMode]?.let { runCatching { PlanMode.valueOf(it) }.getOrNull() } ?: PlanMode.SIMPLE,
            postWake = PostWakePrefs(
                enabled = this[Keys.pwEnabled] ?: false,
                ring = this[Keys.pwRing] ?: true,
                notify = this[Keys.pwNotify] ?: true,
                lockScreen = this[Keys.pwLockScreen] ?: true,
                ringCount = this[Keys.pwRingCount] ?: 0,
                hintDismissed = this[Keys.pwHintDismissed] ?: false,
            ),
            postWakeSession = toPostWakeSession(),
        )
    }

    private fun Preferences.toPostWakeSession(): PostWakeSession? {
        val day = this[Keys.pwDay] ?: return null
        val wake = this[Keys.pwWake] ?: return null
        val exit = this[Keys.pwExit] ?: return null
        return PostWakeSession(
            day = day,
            wakeAtMillis = wake,
            exitAtMillis = exit,
            taskIds = this[Keys.pwTaskIds].orEmpty().split(',').mapNotNull { it.toLongOrNull() },
            msPerMinute = this[Keys.pwScale] ?: PostWakeTimeline.MINUTE_MILLIS,
            events = PostWakeTimeline.decode(this[Keys.pwEvents]),
            lateAlerted = this[Keys.pwLateAlerted] ?: false,
        )
    }

    suspend fun setExitTime(time: LocalTime) = context.dataStore.edit {
        it[Keys.exitMinuteOfDay] = time.hour * 60 + time.minute
    }

    suspend fun setOnboardingDone() = context.dataStore.edit { it[Keys.onboardingDone] = true }

    suspend fun setNotifyOnExit(value: Boolean) = context.dataStore.edit { it[Keys.notifyOnExit] = value }

    suspend fun setExitText(value: String) = context.dataStore.edit { it[Keys.exitText] = value }

    suspend fun setActiveAlarm(alarm: ActiveAlarm?) = context.dataStore.edit {
        if (alarm == null) {
            it.remove(Keys.alarmWakeAt); it.remove(Keys.alarmExitAt)
        } else {
            it[Keys.alarmWakeAt] = alarm.wakeAtMillis; it[Keys.alarmExitAt] = alarm.exitAtMillis
        }
    }

    suspend fun isLegacyMigrated(): Boolean = context.dataStore.data.first()[Keys.legacyMigrated] ?: false

    suspend fun markLegacyMigrated() = context.dataStore.edit { it[Keys.legacyMigrated] = true }

    suspend fun lastSeenVersion(): Int = context.dataStore.data.first()[Keys.lastSeenVersion] ?: 0

    suspend fun setLastSeenVersion(code: Int) = context.dataStore.edit { it[Keys.lastSeenVersion] = code }

    suspend fun setAlarmMode(mode: AlarmMode) = context.dataStore.edit {
        it[Keys.alarmMode] = mode.name; it[Keys.alarmIntroSeen] = true
    }

    suspend fun setAlarmIntroSeen() = context.dataStore.edit { it[Keys.alarmIntroSeen] = true }

    suspend fun updateRinging(transform: (RingingPrefs) -> RingingPrefs) = context.dataStore.edit {
        val new = transform(it.toSettings().ringing)
        it[Keys.ringtone] = new.ringtone
        it[Keys.vibrate] = new.vibrate
        it[Keys.snoozeMinutes] = new.snoozeMinutes
        it[Keys.challenge] = new.challenge.name
        it[Keys.mathOps] = new.mathOps
        it[Keys.shakeCount] = new.shakeCount
        it[Keys.qrValue] = new.qrValue
    }

    suspend fun setPausedUntil(epochDay: Long?) = context.dataStore.edit {
        if (epochDay == null) it.remove(Keys.pausedUntil) else it[Keys.pausedUntil] = epochDay
    }

    suspend fun setLastRungDay(epochDay: Long) = context.dataStore.edit { it[Keys.lastRungDay] = epochDay }

    suspend fun setArmedDay(epochDay: Long?) = context.dataStore.edit {
        if (epochDay == null) it.remove(Keys.armedDay) else it[Keys.armedDay] = epochDay
    }

    suspend fun setPreAlarmNotify(value: Boolean) = context.dataStore.edit { it[Keys.preAlarmNotify] = value }

    suspend fun setPreAlarmMinutes(value: Int) = context.dataStore.edit { it[Keys.preAlarmMinutes] = value }

    suspend fun setEveningSummary(value: Boolean) = context.dataStore.edit { it[Keys.eveningSummary] = value }

    suspend fun setEveningMinute(value: Int) = context.dataStore.edit { it[Keys.eveningMinute] = value }

    suspend fun setHintDismissed() = context.dataStore.edit { it[Keys.hintDismissed] = true }

    suspend fun setBufferHintDismissed() = context.dataStore.edit { it[Keys.bufferHintDismissed] = true }

    suspend fun setPlanMode(mode: PlanMode) = context.dataStore.edit { it[Keys.planMode] = mode.name }

    suspend fun updatePostWake(transform: (PostWakePrefs) -> PostWakePrefs) = context.dataStore.edit {
        val new = transform(it.toSettings().postWake)
        it[Keys.pwEnabled] = new.enabled
        it[Keys.pwRing] = new.ring
        it[Keys.pwNotify] = new.notify
        it[Keys.pwLockScreen] = new.lockScreen
        it[Keys.pwRingCount] = new.ringCount
        it[Keys.pwHintDismissed] = new.hintDismissed
    }

    suspend fun startPostWakeSession(session: PostWakeSession) = context.dataStore.edit {
        it[Keys.pwDay] = session.day
        it[Keys.pwWake] = session.wakeAtMillis
        it[Keys.pwExit] = session.exitAtMillis
        it[Keys.pwTaskIds] = session.taskIds.joinToString(",")
        it[Keys.pwScale] = session.msPerMinute
        it[Keys.pwEvents] = PostWakeTimeline.encode(session.events)
        it[Keys.pwLateAlerted] = session.lateAlerted
    }

    suspend fun markPostWakeLateAlerted() = context.dataStore.edit { it[Keys.pwLateAlerted] = true }

    /** Atomic read-modify-write of the event log: two quick taps never lose one. Returns the new session. */
    suspend fun appendPostWakeEvent(event: PostWakeEvent): PostWakeSession? {
        var result: PostWakeSession? = null
        context.dataStore.edit {
            val session = it.toPostWakeSession() ?: return@edit
            val updated = session.copy(events = session.events + event)
            it[Keys.pwEvents] = PostWakeTimeline.encode(updated.events)
            result = updated
        }
        return result
    }

    suspend fun clearPostWakeSession() = context.dataStore.edit {
        it.remove(Keys.pwDay); it.remove(Keys.pwWake); it.remove(Keys.pwExit)
        it.remove(Keys.pwTaskIds); it.remove(Keys.pwScale); it.remove(Keys.pwEvents); it.remove(Keys.pwLateAlerted)
    }

    /** Counts a real ring (not a test, not a snooze) to time the one-off proposal. */
    suspend fun incrementRingCount() = context.dataStore.edit {
        it[Keys.pwRingCount] = (it[Keys.pwRingCount] ?: 0) + 1
    }

    suspend fun setPostWakeHintDismissed() = context.dataStore.edit { it[Keys.pwHintDismissed] = true }

    suspend fun isPlanMigrated(): Boolean = context.dataStore.data.first()[Keys.planMigrated] ?: false

    suspend fun markPlanMigrated() = context.dataStore.edit { it[Keys.planMigrated] = true }
}
