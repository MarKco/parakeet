package com.ilsecondodasinistra.parakeet.sync

import com.ilsecondodasinistra.parakeet.data.SettingsRepository
import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Backup in one `.parakeet` file. Restoring merges (it never wipes what is newer here). */
@Singleton
class BackupManager @Inject constructor(
    private val store: RoomSyncStore,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    suspend fun export(): String = json.encodeToString(
        BackupFile.serializer(),
        BackupFile(createdAt = clock.millis(), state = store.fullSnapshot(), device = DeviceSettings(settings.deviceValues())),
    )

    /** Number of things changed, or why the file is not usable. */
    suspend fun restore(text: String): Result<Int> {
        val file = try { json.decodeFromString(BackupFile.serializer(), text) } catch (_: Exception) {
            return Result.failure(IllegalArgumentException("unreadable"))
        }
        if (file.format != BackupFile.FORMAT) return Result.failure(IllegalArgumentException("format"))
        if (file.version > BackupFile.VERSION) return Result.failure(IllegalArgumentException("newer"))
        val merged = store.fullSnapshot().mergedWith(file.state).withExclusiveDays()
        val changed = store.apply(merged)
        settings.applyDeviceValues(file.device.values, onlyMissing = true)
        return Result.success(changed)
    }
}
