package com.ilsecondodasinistra.parakeet.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
    val preAlarmNotify: Boolean = true,  // "alarm at 6:45 · Skip" about 2 h before
    val eveningSummary: Boolean = false, // "tomorrow: alarm 6:45" the evening before
    val eveningMinute: Int = 21 * 60 + 30,
    val hintDismissed: Boolean = false,  // "ring on other days too?" hint
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
        val eveningSummary = booleanPreferencesKey("evening_summary")
        val eveningMinute = intPreferencesKey("evening_minute")
        val hintDismissed = booleanPreferencesKey("plan_hint_dismissed")
        val planMigrated = booleanPreferencesKey("plan_migrated")
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
                eveningSummary = this[Keys.eveningSummary] ?: false,
                eveningMinute = this[Keys.eveningMinute] ?: (21 * 60 + 30),
                hintDismissed = this[Keys.hintDismissed] ?: false,
            ),
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

    suspend fun setEveningSummary(value: Boolean) = context.dataStore.edit { it[Keys.eveningSummary] = value }

    suspend fun setEveningMinute(value: Int) = context.dataStore.edit { it[Keys.eveningMinute] = value }

    suspend fun setHintDismissed() = context.dataStore.edit { it[Keys.hintDismissed] = true }

    suspend fun isPlanMigrated(): Boolean = context.dataStore.data.first()[Keys.planMigrated] ?: false

    suspend fun markPlanMigrated() = context.dataStore.edit { it[Keys.planMigrated] = true }
}
