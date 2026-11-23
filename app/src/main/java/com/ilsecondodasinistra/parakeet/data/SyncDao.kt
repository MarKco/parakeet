package com.ilsecondodasinistra.parakeet.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.util.UUID

/** A fresh identity for something that will be shared between devices. */
fun newUid(): String = UUID.randomUUID().toString().replace("-", "")

/** What was deleted here, kept so the other devices delete it too. [kind]: task, routine, dayplan. [key]: the uid, or the epoch day. */
@Entity(tableName = "sync_tombstones", primaryKeys = ["kind", "key"])
data class TombstoneEntity(val kind: String, val key: String, val localId: Long, val deletedAt: Long) {
    companion object {
        const val TASK = "task"
        const val ROUTINE = "routine"
        const val DAY_PLAN = "dayplan"
    }
}

@Dao
interface SyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTombstone(tombstone: TombstoneEntity)

    @Query("SELECT * FROM sync_tombstones")
    suspend fun tombstones(): List<TombstoneEntity>

    @Query("SELECT * FROM sync_tombstones WHERE kind = :kind AND localId = :localId")
    suspend fun tombstoneOfLocal(kind: String, localId: Long): TombstoneEntity?

    @Query("DELETE FROM sync_tombstones WHERE kind = :kind AND `key` = :key")
    suspend fun deleteTombstone(kind: String, key: String)

    @Query("DELETE FROM sync_tombstones WHERE deletedAt < :before")
    suspend fun purgeTombstonesBefore(before: Long)

    // ---- whole tables, for one sync round ------------------------------------------------------------

    @Query("SELECT * FROM tasks")
    suspend fun tasks(): List<TaskEntity>

    @Query("SELECT * FROM routines")
    suspend fun routines(): List<RoutineEntity>

    @Query("SELECT * FROM routine_tasks ORDER BY routineId, position, taskId")
    suspend fun routineTasks(): List<RoutineTaskEntity>

    @Query("SELECT * FROM day_plans")
    suspend fun dayPlans(): List<DayPlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTask(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putRoutine(routine: RoutineEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putDayPlan(plan: DayPlanEntity)

    @Query("UPDATE tasks SET updatedAt = :now WHERE id IN (:ids) AND updatedAt = 0")
    suspend fun promoteTasks(ids: List<Long>, now: Long)

    @Query("UPDATE routines SET updatedAt = :now WHERE id IN (:ids) AND updatedAt = 0")
    suspend fun promoteRoutines(ids: List<Long>, now: Long)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun removeTask(id: Long)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun removeRoutine(id: Long)

    @Query("DELETE FROM routine_tasks WHERE routineId = :id")
    suspend fun removeRoutineTasks(id: Long)

    @Query("DELETE FROM day_plans WHERE epochDay = :epochDay")
    suspend fun removeDayPlan(epochDay: Long)

    @Query("DELETE FROM day_plans WHERE routineId = :routineId")
    suspend fun removeDayPlansOf(routineId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun putRoutineTasks(rows: List<RoutineTaskEntity>)
}
