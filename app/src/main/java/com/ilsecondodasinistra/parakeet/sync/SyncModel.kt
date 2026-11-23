package com.ilsecondodasinistra.parakeet.sync

import kotlinx.serialization.Serializable

/** An activity of the catalog, as it travels. [checked] is the device's own tick: only the backup carries it. */
@Serializable
data class TaskRec(
    val uid: String,
    val name: String = "",
    val minutes: Int = 0,
    val buffer: Boolean = false,
    val position: Int = 0,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val checked: Boolean? = null,
)

/** A weekly routine with the uids of its activities. */
@Serializable
data class RoutineRec(
    val uid: String,
    val name: String = "",
    val color: Int = 0,
    val exitMinute: Int = 0,
    val daysMask: Int = 0,
    val enabled: Boolean = true,
    val position: Int = 0,
    val taskUids: List<String> = emptyList(),
    val updatedAt: Long,
    val deleted: Boolean = false,
)

/** A one-time change for one date (key: the epoch day). */
@Serializable
data class DayPlanRec(
    val epochDay: Long,
    val kind: String = "",
    val routineUid: String? = null,
    val exitMinute: Int? = null,
    val taskUids: List<String> = emptyList(),
    val updatedAt: Long,
    val deleted: Boolean = false,
)

/** A shared setting, as text, with the time it was last changed. */
@Serializable
data class StampedValue(val value: String, val updatedAt: Long)

/**
 * Everything that travels between devices. Each entry has the time of its last edit (a deletion is an entry with
 * `deleted = true`, a tombstone). The merge is by entry: the most recent edit wins, on a tie the local one stays.
 */
@Serializable
data class SyncState(
    val tasks: Map<String, TaskRec> = emptyMap(),
    val routines: Map<String, RoutineRec> = emptyMap(),
    val dayPlans: Map<String, DayPlanRec> = emptyMap(),
    val settings: Map<String, StampedValue> = emptyMap(),
) {
    fun mergedWith(other: SyncState): SyncState = SyncState(
        tasks = tasks.newer(other.tasks) { it.updatedAt },
        routines = routines.newer(other.routines) { it.updatedAt },
        dayPlans = dayPlans.newer(other.dayPlans) { it.updatedAt },
        settings = settings.newer(other.settings) { it.updatedAt },
    )

    /** Without what is useless: plans of days already gone, tombstones older than a year. */
    fun pruned(todayEpochDay: Long, nowMillis: Long): SyncState {
        val limit = nowMillis - TOMBSTONE_KEEP_MILLIS
        return SyncState(
            tasks = tasks.filterValues { !(it.deleted && it.updatedAt < limit) },
            routines = routines.filterValues { !(it.deleted && it.updatedAt < limit) },
            dayPlans = dayPlans.filterValues { it.epochDay >= todayEpochDay - 1 && !(it.deleted && it.updatedAt < limit) },
            settings = settings,
        )
    }

    /**
     * Two routines that are ON never share a weekday (as `PlanRepository.setDays` enforces): where the merge gave one to
     * two, the most recently edited keeps it. A routine that is off keeps its days and never takes part. Nothing is
     * stamped, so every device reaches the same result on its own.
     */
    fun withExclusiveDays(): SyncState {
        val live = routines.values.filter { !it.deleted && it.enabled }
        if (live.size < 2) return this
        val winner = (0..6).associateWith { bit ->
            live.filter { it.daysMask and (1 shl bit) != 0 }.maxWithOrNull(compareBy<RoutineRec> { it.updatedAt }.thenBy { it.uid })?.uid
        }
        val fixed = routines.mapValues { (uid, r) ->
            if (r.deleted || !r.enabled) r
            else {
                val mask = (0..6).fold(0) { acc, bit -> if (r.daysMask and (1 shl bit) != 0 && winner[bit] == uid) acc or (1 shl bit) else acc }
                if (mask == r.daysMask) r else r.copy(daysMask = mask)
            }
        }
        return copy(routines = fixed)
    }

    companion object {
        const val TOMBSTONE_KEEP_MILLIS = 365L * 24 * 3600 * 1000
    }
}

/** Phone-bound settings (alarm mode, exit notification…): written in the device's own file, read only by "this is that device". */
@Serializable
data class DeviceSettings(val values: Map<String, String> = emptyMap())

/** What a device writes in the shared folder: its complete state. Only this device writes this file. */
@Serializable
data class SyncFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val deviceId: String,
    val deviceName: String = "",
    val writtenAt: Long = 0,
    val state: SyncState = SyncState(),
    val device: DeviceSettings = DeviceSettings(),
) {
    companion object {
        const val FORMAT = "parakeet-sync"
        const val VERSION = 1
        const val RESOLVED_KEEP_DAYS = 30
    }
}

/** Backup in one file (`.parakeet`): the sync state plus the ticks and the phone's own settings. Restoring merges. */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val createdAt: Long,
    val state: SyncState,
    val device: DeviceSettings = DeviceSettings(),
) {
    companion object {
        const val FORMAT = "parakeet-backup"
        const val VERSION = 1
    }
}

/** A device seen in the folder. */
data class DeviceInfo(val deviceId: String, val deviceName: String, val writtenAt: Long, val device: DeviceSettings)

@Serializable
data class SyncResult(
    val ok: Boolean,
    /** Devices whose files were read (names). */
    val devices: List<String> = emptyList(),
    /** Changes made to this device. */
    val changed: Int = 0,
    val wrote: Boolean = false,
    /** `.sync-conflict-` copies merged and archived. */
    val resolved: Int = 0,
    val unreadable: Int = 0,
    val error: String? = null,
    val at: Long = 0,
)

/** File names in the shared folder. */
object SyncNames {
    const val PREFIX = "parakeet-"
    private const val CONFLICT = ".sync-conflict-"
    private const val RESOLVED = ".resolved-"

    fun own(deviceId: String) = "$PREFIX$deviceId.json"
    fun isState(name: String) = name.startsWith(PREFIX) && name.endsWith(".json")
    fun isConflict(name: String) = isState(name) && CONFLICT in name
    fun isResolved(name: String) = isState(name) && RESOLVED in name

    /** `parakeet-<id>.json`, `parakeet-<id>.sync-conflict-…json` → `<id>`. */
    fun deviceIdOf(name: String): String = name.removePrefix(PREFIX).substringBefore('.')

    /** The copy renamed once merged: no longer a conflict (nobody reads it), and still `.json`. */
    fun resolvedName(name: String, yyyymmdd: String): String {
        val id = deviceIdOf(name)
        val rest = name.removePrefix("$PREFIX$id").removePrefix(CONFLICT).removeSuffix(".json")
        return "$PREFIX$id$RESOLVED$yyyymmdd-$rest.json"
    }

    /** The date in the name of an archived copy, `yyyymmdd`, or null. */
    fun resolvedDate(name: String): String? =
        name.substringAfter(RESOLVED, "").take(8).takeIf { it.length == 8 && it.all(Char::isDigit) }
}

/** Union of two maps by key; for a key in both, the entry with the later stamp wins and on a tie this one stays. */
private fun <T> Map<String, T>.newer(other: Map<String, T>, stamp: (T) -> Long): Map<String, T> {
    val out = LinkedHashMap(this)
    for ((key, theirs) in other) {
        val mine = out[key]
        if (mine == null || stamp(theirs) > stamp(mine)) out[key] = theirs
    }
    return out
}
