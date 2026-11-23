package com.ilsecondodasinistra.parakeet.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Weekly template. [daysMask]: bit 0 = Monday … bit 6 = Sunday. */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val exitMinute: Int,
    val daysMask: Int,
    val enabled: Boolean,
    val position: Int,
    @ColumnInfo(defaultValue = "''") val uid: String = "",
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
)

/** Which catalog activities a routine uses, and in which order ([position]). Rows of deleted tasks are harmless (ignored) and come back on undo. */
@Entity(tableName = "routine_tasks", primaryKeys = ["routineId", "taskId"])
data class RoutineTaskEntity(val routineId: Long, val taskId: Long, @ColumnInfo(defaultValue = "0") val position: Int = 0)

/** One-time change for one date. [kind]: SKIP, ROUTINE, CUSTOM. [taskIds]: comma separated (CUSTOM only). */
@Entity(tableName = "day_plans")
data class DayPlanEntity(
    @PrimaryKey val epochDay: Long,
    val kind: String,
    val routineId: Long?,
    val exitMinute: Int?,
    val taskIds: String,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
)

@Dao
interface PlanDao {
    @Query("SELECT * FROM routines ORDER BY position, id")
    fun observeRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routine_tasks ORDER BY routineId, position, taskId")
    fun observeRoutineTasks(): Flow<List<RoutineTaskEntity>>

    @Query("SELECT * FROM day_plans ORDER BY epochDay")
    fun observeDayPlans(): Flow<List<DayPlanEntity>>

    @Query("SELECT COUNT(*) FROM routines")
    suspend fun routineCount(): Int

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun routineById(id: Long): RoutineEntity?

    @Query("SELECT * FROM day_plans WHERE epochDay = :epochDay")
    suspend fun dayPlanOf(epochDay: Long): DayPlanEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM routines")
    suspend fun nextRoutinePosition(): Int

    @Insert
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Update
    suspend fun updateRoutines(routines: List<RoutineEntity>)

    @Query("SELECT * FROM routines")
    suspend fun routines(): List<RoutineEntity>

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutine(id: Long)

    @Query("DELETE FROM routine_tasks WHERE routineId = :routineId")
    suspend fun clearRoutineTasks(routineId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRoutineTasks(rows: List<RoutineTaskEntity>)

    @Transaction
    suspend fun replaceRoutineTasks(routineId: Long, taskIds: List<Long>) {
        clearRoutineTasks(routineId)
        insertRoutineTasks(taskIds.mapIndexed { i, id -> RoutineTaskEntity(routineId, id, i) })
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDayPlan(plan: DayPlanEntity)

    @Query("DELETE FROM day_plans WHERE epochDay = :epochDay")
    suspend fun deleteDayPlan(epochDay: Long)

    @Query("DELETE FROM day_plans WHERE routineId = :routineId")
    suspend fun deleteDayPlansOf(routineId: Long)

    @Query("DELETE FROM day_plans WHERE epochDay < :epochDay")
    suspend fun deleteDayPlansBefore(epochDay: Long)
}

/** 4.0/5.0 → 5.1: weekly routines and one-time day plans. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `routines` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`color` INTEGER NOT NULL, `exitMinute` INTEGER NOT NULL, `daysMask` INTEGER NOT NULL, " +
                "`enabled` INTEGER NOT NULL, `position` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `routine_tasks` (`routineId` INTEGER NOT NULL, `taskId` INTEGER NOT NULL, " +
                "PRIMARY KEY(`routineId`, `taskId`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `day_plans` (`epochDay` INTEGER NOT NULL, `kind` TEXT NOT NULL, `routineId` INTEGER, " +
                "`exitMinute` INTEGER, `taskIds` TEXT NOT NULL, PRIMARY KEY(`epochDay`))",
        )
    }
}

/** 5.1 → 5.2: an activity can be the "time for the unexpected" cushion (not an activity of its own). */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `buffer` INTEGER NOT NULL DEFAULT 0")
    }
}

/** 5.2 → 5.3: identity and last-edit time for the sync, and the tombstones of what was deleted. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (table in listOf("tasks", "routines")) {
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
            // What exists now was made by the user: it travels (updatedAt 1 = "old", it loses against any real edit)
            db.execSQL("UPDATE `$table` SET `uid` = lower(hex(randomblob(16))), `updatedAt` = 1")
        }
        db.execSQL("ALTER TABLE `day_plans` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `day_plans` SET `updatedAt` = 1")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_tombstones` (`kind` TEXT NOT NULL, `key` TEXT NOT NULL, " +
                "`localId` INTEGER NOT NULL, `deletedAt` INTEGER NOT NULL, PRIMARY KEY(`kind`, `key`))",
        )
    }
}

/** 5.4 → 5.5: the activities of a routine have an order of their own (until now: the order of the catalog). */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `routine_tasks` ADD COLUMN `position` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `routine_tasks` SET `position` = COALESCE((SELECT t.`position` FROM `tasks` t WHERE t.`id` = `routine_tasks`.`taskId`), 0)")
    }
}
