package com.ilsecondodasinistra.parakeet.sync

import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** A shared folder (Syncthing, Dropbox, Drive…): reached through the system file access (`SafSyncFolder`). */
interface SyncFolder {
    /** Names of the files in the folder (not sub-folders). */
    fun list(): List<String>
    fun read(name: String): String?
    fun write(name: String, text: String)
    fun rename(name: String, newName: String)
    fun delete(name: String)
}

/** The local data as the engine sees it. */
interface SyncStore {
    /** What this device would share now. Seeds still untouched are left out unless something shared refers to them. */
    suspend fun snapshot(): SyncState

    /** Writes into the local data whatever [merged] has newer; returns how many things changed. */
    suspend fun apply(merged: SyncState): Int

    /** The settings that belong to this phone. */
    suspend fun deviceSettings(): DeviceSettings
}

/**
 * One sync round: read the files of the other devices and merge them, then write this device's own file when
 * its state changed. One writer per file, so Syncthing has nothing to conflict on; if it makes a
 * `.sync-conflict-` copy anyway, that is a complete state like the others: it is merged, then archived.
 */
object SyncEngine {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val dayFormat = DateTimeFormatter.BASIC_ISO_DATE

    fun encode(file: SyncFile): String = json.encodeToString(SyncFile.serializer(), file)
    fun decode(text: String): SyncFile = json.decodeFromString(SyncFile.serializer(), text)

    private fun read(folder: SyncFolder, name: String): SyncFile? {
        val text = folder.read(name) ?: return null
        val f = try { decode(text) } catch (_: Exception) { return null }
        return f.takeIf { it.format == SyncFile.FORMAT && it.version <= SyncFile.VERSION }
    }

    /** The other devices in the folder (for the first connection): latest file of each, newest first. */
    fun devices(folder: SyncFolder, exceptDeviceId: String? = null): List<DeviceInfo> =
        folder.list().filter { SyncNames.isState(it) && !SyncNames.isConflict(it) && !SyncNames.isResolved(it) }
            .mapNotNull { name -> read(folder, name) }
            .filter { it.deviceId != exceptDeviceId }
            .map { DeviceInfo(it.deviceId, it.deviceName, it.writtenAt, it.device) }
            .sortedByDescending { it.writtenAt }

    /** Does the folder hold files of other devices? */
    fun hasOtherDevices(folder: SyncFolder, deviceId: String): Boolean = devices(folder, deviceId).isNotEmpty()

    /**
     * [readOwnFile]: also read this device's own old file (first round after "this is that device": it may hold the only copy of the data).
     */
    suspend fun run(
        folder: SyncFolder,
        store: SyncStore,
        deviceId: String,
        deviceName: String,
        nowMillis: Long,
        todayEpochDay: Long,
        readOwnFile: Boolean = false,
    ): SyncResult {
        return try {
            val own = SyncNames.own(deviceId)
            val names = folder.list().sorted()
            var merged = store.snapshot()
            val local = merged
            val seen = mutableListOf<String>()
            val toArchive = mutableListOf<String>()
            var unreadable = 0
            for (name in names) {
                if (!SyncNames.isState(name) || SyncNames.isResolved(name)) continue
                val conflict = SyncNames.isConflict(name)
                if (name == own && !readOwnFile) continue
                val f = read(folder, name)
                if (f == null) { unreadable++; continue }
                merged = merged.mergedWith(f.state.pruned(todayEpochDay, nowMillis))
                if (conflict) toArchive += name
                else if (f.deviceId != deviceId) seen += f.deviceName.ifEmpty { f.deviceId }
            }
            merged = merged.pruned(todayEpochDay, nowMillis).withExclusiveDays()
            val changed = if (merged != local) store.apply(merged) else 0

            // our complete state, written when it differs from what is in the folder (compare the state, not the time)
            val state = store.snapshot()
            val device = store.deviceSettings()
            val old = if (own in names) read(folder, own) else null
            var wrote = false
            if (old == null || old.state != state || old.deviceName != deviceName || old.device != device) {
                folder.write(own, encode(SyncFile(deviceId = deviceId, deviceName = deviceName, writtenAt = nowMillis, state = state, device = device)))
                wrote = true
            }

            // the merged conflict copies are archived, the old archived ones are deleted
            val today = LocalDate.ofEpochDay(todayEpochDay).format(dayFormat)
            var resolved = 0
            for (name in toArchive) {
                try { folder.rename(name, SyncNames.resolvedName(name, today)); resolved++ } catch (_: Exception) { /* merged again next round */ }
            }
            val limit = LocalDate.ofEpochDay(todayEpochDay - SyncFile.RESOLVED_KEEP_DAYS).format(dayFormat)
            for (name in names) {
                val d = if (SyncNames.isResolved(name)) SyncNames.resolvedDate(name) else null
                if (d != null && d < limit) try { folder.delete(name) } catch (_: Exception) { }
            }
            SyncResult(true, seen.distinct(), changed, wrote, resolved, unreadable, null, nowMillis)
        } catch (e: Exception) {
            SyncResult(false, error = e.message ?: e.toString(), at = nowMillis)
        }
    }
}
