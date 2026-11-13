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

data class Settings(
    val exitTime: LocalTime?,           // null until the user chooses one
    val onboardingDone: Boolean,
    val notifyOnExit: Boolean,
    val exitText: String,               // empty = default text
    val activeAlarm: ActiveAlarm?,
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
}
