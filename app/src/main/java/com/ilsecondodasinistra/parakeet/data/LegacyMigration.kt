package com.ilsecondodasinistra.parakeet.data

import android.content.Context
import androidx.core.content.edit
import com.ilsecondodasinistra.parakeet.ThingToDo
import com.ilsecondodasinistra.parakeet.domain.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Decodes what Parakeet 3.x stored with its ObjectSerializer (Java serialization, hex-like a..p encoding). */
object LegacyDecoder {

    fun decodeBytes(str: String): ByteArray {
        val bytes = ByteArray(str.length / 2)
        for (i in str.indices step 2) {
            bytes[i / 2] = (((str[i] - 'a') shl 4) + (str[i + 1] - 'a')).toByte()
        }
        return bytes
    }

    /** Returns the saved tasks, or an empty list when [encoded] is blank or unreadable. */
    fun decodeTasks(encoded: String?): List<Task> {
        if (encoded.isNullOrEmpty()) return emptyList()
        return try {
            ObjectInputStream(ByteArrayInputStream(decodeBytes(encoded))).use { stream ->
                val list = stream.readObject() as? List<*> ?: return emptyList()
                list.filterIsInstance<ThingToDo>().mapIndexed { index, old ->
                    Task(name = old.name, minutes = old.length, checked = old.checked, position = index)
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

data class MigrationResult(val wasExistingUser: Boolean)

@Singleton
class LegacyMigration @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
) {
    /** Runs once. Reads v3.x SharedPreferences, imports into Room/DataStore, then removes the old keys. */
    suspend fun migrateIfNeeded(): MigrationResult {
        if (settings.isLegacyMigrated()) return MigrationResult(wasExistingUser = false)

        // v3.x saved activity-private prefs via getPreferences(0) => file named after the activity class.
        val old = context.getSharedPreferences("ParakeetMain", Context.MODE_PRIVATE)
        val oldDefault = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)

        val hasLegacy = old.contains("toDoTasks") || old.contains("dateTimeToLeave")
        if (hasLegacy) {
            val imported = LegacyDecoder.decodeTasks(old.getString("toDoTasks", null))
            if (imported.isNotEmpty() && tasks.count() == 0) tasks.importAll(imported)

            val leaveMillis = old.getLong("dateTimeToLeave", 0L)
            if (leaveMillis != 0L) {
                val time: LocalTime = Instant.ofEpochMilli(leaveMillis).atZone(ZoneId.systemDefault()).toLocalTime()
                settings.setExitTime(time.withSecond(0).withNano(0))
            }
            if (oldDefault.contains("notification_on_exit")) {
                settings.setNotifyOnExit(oldDefault.getBoolean("notification_on_exit", true))
            }
            oldDefault.getString("exit_noti_text", null)?.let { settings.setExitText(it) }
            settings.setOnboardingDone()

            old.edit { remove("toDoTasks"); remove("dateTimeToLeave"); remove("dateTimeToWakeUp") }
        }
        settings.markLegacyMigrated()
        return MigrationResult(wasExistingUser = hasLegacy)
    }
}
