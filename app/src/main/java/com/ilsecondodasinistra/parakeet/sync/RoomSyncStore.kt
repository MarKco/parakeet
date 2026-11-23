package com.ilsecondodasinistra.parakeet.sync

import androidx.room.withTransaction
import com.ilsecondodasinistra.parakeet.data.AppDatabase
import com.ilsecondodasinistra.parakeet.data.DayPlanEntity
import com.ilsecondodasinistra.parakeet.data.RoutineEntity
import com.ilsecondodasinistra.parakeet.data.RoutineTaskEntity
import com.ilsecondodasinistra.parakeet.data.SettingsRepository
import com.ilsecondodasinistra.parakeet.data.SyncDao
import com.ilsecondodasinistra.parakeet.data.TaskEntity
import com.ilsecondodasinistra.parakeet.data.TombstoneEntity
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** The Room database and DataStore seen as a [SyncState]. */
@Singleton
class RoomSyncStore @Inject constructor(
    private val db: AppDatabase,
    private val dao: SyncDao,
    private val settings: SettingsRepository,
    private val syncSettings: SyncSettingsRepository,
    private val clock: Clock,
) : SyncStore {

    private fun today() = LocalDate.now(clock).toEpochDay()

    override suspend fun snapshot(): SyncState = build(full = false)

    /** Everything, for a backup: seeds and ticks included. */
    suspend fun fullSnapshot(): SyncState = build(full = true)

    override suspend fun deviceSettings() = DeviceSettings(settings.deviceValues())

    private suspend fun stampedSettings(): Map<String, StampedValue> {
        val current = SharedSettings.values(settings.current())
        val stamps = syncSettings.refreshStamps(current, SharedSettings.defaults, clock.millis())
        return current.filterKeys { (stamps[it] ?: 0L) > 0 }.mapValues { (k, v) -> StampedValue(v, stamps.getValue(k)) }
    }

    private suspend fun build(full: Boolean): SyncState {
        val shared = stampedSettings()
        return db.withTransaction {
            val now = clock.millis()
            dao.purgeTombstonesBefore(now - SyncState.TOMBSTONE_KEEP_MILLIS)
            if (!full) promoteReferencedSeeds(now)
            val tasks = dao.tasks()
            val routines = dao.routines()
            val links = dao.routineTasks()
            val plans = dao.dayPlans()
            val tombs = dao.tombstones()
            val minDay = today() - 1
            fun travels(updatedAt: Long) = full || updatedAt > 0
            val taskUid = tasks.associate { it.id to it.uid }
            val uidOfRoutine = routines.associate { it.id to it.uid }

            val taskRecs = LinkedHashMap<String, TaskRec>()
            for (t in tasks.filter { travels(it.updatedAt) }) {
                taskRecs[t.uid] = TaskRec(t.uid, t.name, t.minutes, t.buffer, t.position, t.updatedAt, false, if (full) t.checked else null)
            }
            for (d in tombs.filter { it.kind == TombstoneEntity.TASK }) {
                val live = taskRecs[d.key]
                if (live == null || d.deletedAt > live.updatedAt) taskRecs[d.key] = TaskRec(d.key, updatedAt = d.deletedAt, deleted = true)
            }
            val routineRecs = LinkedHashMap<String, RoutineRec>()
            for (r in routines.filter { travels(it.updatedAt) }) {
                val members = links.filter { it.routineId == r.id }.mapNotNull { taskUid[it.taskId] }.sorted()
                routineRecs[r.uid] = RoutineRec(r.uid, r.name, r.color, r.exitMinute, r.daysMask, r.enabled, r.position, members, r.updatedAt)
            }
            for (d in tombs.filter { it.kind == TombstoneEntity.ROUTINE }) {
                val live = routineRecs[d.key]
                if (live == null || d.deletedAt > live.updatedAt) routineRecs[d.key] = RoutineRec(d.key, updatedAt = d.deletedAt, deleted = true)
            }
            val planRecs = LinkedHashMap<String, DayPlanRec>()
            for (p in plans.filter { travels(it.updatedAt) && (full || it.epochDay >= minDay) }) {
                val routineUid = p.routineId?.let { uidOfRoutine[it] ?: return@let "?" }
                if (routineUid == "?") continue // its routine is gone from here: nothing to share
                val members = p.taskIds.split(',').mapNotNull { it.trim().toLongOrNull() }.mapNotNull { taskUid[it] }.sorted()
                planRecs[p.epochDay.toString()] = DayPlanRec(p.epochDay, p.kind, routineUid, p.exitMinute, members, p.updatedAt)
            }
            for (d in tombs.filter { it.kind == TombstoneEntity.DAY_PLAN }) {
                val day = d.key.toLongOrNull() ?: continue
                if (!full && day < minDay) continue
                val live = planRecs[d.key]
                if (live == null || d.deletedAt > live.updatedAt) planRecs[d.key] = DayPlanRec(day, updatedAt = d.deletedAt, deleted = true)
            }
            SyncState(taskRecs.sorted(), routineRecs.sorted(), planRecs.sorted(), shared.sorted())
        }
    }

    /** A seed that something shared points at (a routine, a plan of a day) stops being a seed: the others need it. */
    private suspend fun promoteReferencedSeeds(now: Long) {
        val routines = dao.routines()
        val plans = dao.dayPlans().filter { it.updatedAt > 0 }
        val planRoutines = plans.mapNotNull { it.routineId }.toSet()
        val seedRoutines = routines.filter { it.updatedAt == 0L && it.id in planRoutines }.map { it.id }
        if (seedRoutines.isNotEmpty()) dao.promoteRoutines(seedRoutines, now)
        val traveling = (dao.routines().filter { it.updatedAt > 0 }.map { it.id }).toSet()
        val used = dao.routineTasks().filter { it.routineId in traveling }.map { it.taskId }.toMutableSet()
        plans.forEach { p -> p.taskIds.split(',').mapNotNullTo(used) { it.trim().toLongOrNull() } }
        val seedTasks = dao.tasks().filter { it.updatedAt == 0L && it.id in used }.map { it.id }
        if (seedTasks.isNotEmpty()) dao.promoteTasks(seedTasks, now)
    }

    /** Drops what was only seeded here (never edited, never shared): the folder has the user's own. */
    suspend fun discardUntouchedSeeds() = db.withTransaction {
        promoteReferencedSeeds(clock.millis())
        dao.routines().filter { it.updatedAt == 0L }.forEach { r ->
            dao.removeDayPlansOf(r.id); dao.removeRoutineTasks(r.id); dao.removeRoutine(r.id)
        }
        dao.tasks().filter { it.updatedAt == 0L }.forEach { dao.removeTask(it.id) }
        dao.dayPlans().filter { it.updatedAt == 0L }.forEach { dao.removeDayPlan(it.epochDay) }
    }

    override suspend fun apply(merged: SyncState): Int {
        val appliedSettings = mutableMapOf<String, StampedValue>()
        var n = db.withTransaction {
            var changes = 0
            val minDay = today() - 1

            // activities
            val localTasks = dao.tasks().associateBy { it.uid }.toMutableMap()
            for (rec in merged.tasks.values) {
                val local = localTasks[rec.uid]
                if (rec.deleted) {
                    if (local != null && local.updatedAt <= rec.updatedAt) { dao.removeTask(local.id); localTasks.remove(rec.uid); changes++ }
                    if (local == null || local.updatedAt <= rec.updatedAt) {
                        dao.putTombstone(TombstoneEntity(TombstoneEntity.TASK, rec.uid, local?.id ?: 0, rec.updatedAt))
                    }
                } else {
                    val differs = local == null || local.name != rec.name || local.minutes != rec.minutes ||
                        local.buffer != rec.buffer || local.position != rec.position
                    if (local == null && dao.tombstones().any { it.kind == TombstoneEntity.TASK && it.key == rec.uid && it.deletedAt > rec.updatedAt }) continue
                    if (differs && (local == null || rec.updatedAt >= local.updatedAt)) {
                        val e = TaskEntity(local?.id ?: 0, rec.name, rec.minutes, local?.checked ?: rec.checked ?: true, rec.position, rec.buffer, rec.uid, rec.updatedAt)
                        val id = dao.putTask(e)
                        localTasks[rec.uid] = e.copy(id = if (local != null) local.id else id)
                        dao.deleteTombstone(TombstoneEntity.TASK, rec.uid)
                        changes++
                    }
                }
            }
            val taskId = localTasks.mapValues { it.value.id }

            // routines
            val localRoutines = dao.routines().associateBy { it.uid }.toMutableMap()
            val links = dao.routineTasks()
            for (rec in merged.routines.values) {
                val local = localRoutines[rec.uid]
                if (rec.deleted) {
                    if (local != null && local.updatedAt <= rec.updatedAt) {
                        dao.removeDayPlansOf(local.id); dao.removeRoutineTasks(local.id); dao.removeRoutine(local.id)
                        localRoutines.remove(rec.uid); changes++
                    }
                    if (local == null || local.updatedAt <= rec.updatedAt) {
                        dao.putTombstone(TombstoneEntity(TombstoneEntity.ROUTINE, rec.uid, local?.id ?: 0, rec.updatedAt))
                    }
                } else {
                    val wanted = rec.taskUids.mapNotNull { taskId[it] }.toSet()
                    val have = local?.let { l -> links.filter { it.routineId == l.id }.map { it.taskId }.toSet() }.orEmpty()
                    val differs = local == null || local.name != rec.name || local.color != rec.color || local.exitMinute != rec.exitMinute ||
                        local.daysMask != rec.daysMask || local.enabled != rec.enabled || local.position != rec.position ||
                        (rec.updatedAt > local.updatedAt && wanted != have)
                    if (local == null && dao.tombstones().any { it.kind == TombstoneEntity.ROUTINE && it.key == rec.uid && it.deletedAt > rec.updatedAt }) continue
                    if (differs && (local == null || rec.updatedAt >= local.updatedAt)) {
                        val e = RoutineEntity(local?.id ?: 0, rec.name, rec.color, rec.exitMinute, rec.daysMask, rec.enabled, rec.position, rec.uid, rec.updatedAt)
                        val id = dao.putRoutine(e).let { if (local != null) local.id else it }
                        if (local == null || rec.updatedAt > local.updatedAt) {
                            dao.removeRoutineTasks(id)
                            dao.putRoutineTasks(wanted.map { RoutineTaskEntity(id, it) })
                        }
                        localRoutines[rec.uid] = e.copy(id = id)
                        dao.deleteTombstone(TombstoneEntity.ROUTINE, rec.uid)
                        changes++
                    }
                }
            }
            val routineId = localRoutines.mapValues { it.value.id }

            // plans of a day
            val localPlans = dao.dayPlans().associateBy { it.epochDay }
            for (rec in merged.dayPlans.values) {
                if (rec.epochDay < minDay) continue
                val local = localPlans[rec.epochDay]
                if (rec.deleted) {
                    if (local != null && local.updatedAt <= rec.updatedAt) { dao.removeDayPlan(rec.epochDay); changes++ }
                    if (local == null || local.updatedAt <= rec.updatedAt) {
                        dao.putTombstone(TombstoneEntity(TombstoneEntity.DAY_PLAN, rec.epochDay.toString(), rec.epochDay, rec.updatedAt))
                    }
                } else {
                    val rid = rec.routineUid?.let { routineId[it] ?: return@let -1L }
                    if (rid == -1L) continue // its routine has not arrived (or is gone): tried again next round
                    val e = DayPlanEntity(
                        rec.epochDay, rec.kind, rid, rec.exitMinute,
                        rec.taskUids.mapNotNull { taskId[it] }.sorted().joinToString(","), rec.updatedAt,
                    )
                    val differs = local == null || local.kind != e.kind || local.routineId != e.routineId ||
                        local.exitMinute != e.exitMinute || local.taskIds != e.taskIds
                    if (local == null && dao.tombstones().any { it.kind == TombstoneEntity.DAY_PLAN && it.key == rec.epochDay.toString() && it.deletedAt > rec.updatedAt }) continue
                    if (differs && (local == null || rec.updatedAt > local.updatedAt)) {
                        dao.putDayPlan(e)
                        dao.deleteTombstone(TombstoneEntity.DAY_PLAN, rec.epochDay.toString())
                        changes++
                    }
                }
            }
            changes
        }

        // shared settings
        val stamps = syncSettings.stamps()
        val newer = merged.settings.filter { (k, v) -> k in SharedSettings.keys && v.updatedAt > (stamps[k] ?: 0L) }
        if (newer.isNotEmpty()) {
            SharedSettings.apply(settings, newer.mapValues { it.value.value })
            appliedSettings += newer
            syncSettings.recordApplied(newer)
            n += newer.size
        }
        return n
    }

    private fun <T> Map<String, T>.sorted(): Map<String, T> = entries.sortedBy { it.key }.associate { it.key to it.value }
}
