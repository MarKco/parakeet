package com.ilsecondodasinistra.parakeet.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import com.ilsecondodasinistra.parakeet.domain.Task
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val minutes: Int,
    val checked: Boolean,
    val position: Int,
)

fun TaskEntity.toDomain() = Task(id, name, minutes, checked, position)
fun Task.toEntity() = TaskEntity(id, name, minutes, checked, position)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY position, id")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM tasks")
    suspend fun nextPosition(): Int

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Insert
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET checked = :checked WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [TaskEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
}
