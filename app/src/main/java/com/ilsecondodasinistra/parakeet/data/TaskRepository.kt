package com.ilsecondodasinistra.parakeet.data

import com.ilsecondodasinistra.parakeet.domain.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(private val dao: TaskDao) {

    val tasks: Flow<List<Task>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun count() = dao.count()

    suspend fun add(name: String, minutes: Int, checked: Boolean = true) {
        dao.insert(TaskEntity(name = name.trim(), minutes = minutes, checked = checked, position = dao.nextPosition()))
    }

    suspend fun update(task: Task) = dao.update(task.toEntity())

    suspend fun setChecked(id: Long, checked: Boolean) = dao.setChecked(id, checked)

    suspend fun delete(id: Long) = dao.delete(id)

    /** Re-inserts a removed task at its old position (undo). */
    suspend fun restore(task: Task) {
        dao.insert(task.toEntity().copy(id = 0))
    }

    suspend fun importAll(tasks: List<Task>) {
        dao.insertAll(tasks.mapIndexed { i, t -> t.toEntity().copy(id = 0, position = i) })
    }
}
