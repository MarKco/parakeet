package com.ilsecondodasinistra.parakeet.data

import com.ilsecondodasinistra.parakeet.domain.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The activity catalog. Every change that matters to the other devices stamps `updatedAt`; [seed] things (the
 * onboarding list, the 3.x import) stay at 0: they do not travel and are replaced when this device joins a
 * folder that already has the user's own.
 */
@Singleton
class TaskRepository @Inject constructor(
    private val dao: TaskDao,
    private val sync: SyncDao,
    private val clock: Clock,
) {

    val tasks: Flow<List<Task>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun count() = dao.count()

    suspend fun add(name: String, minutes: Int, checked: Boolean = true, buffer: Boolean = false, seed: Boolean = false): Long =
        dao.insert(
            TaskEntity(
                name = name.trim(), minutes = minutes, checked = checked, position = dao.nextPosition(), buffer = buffer,
                uid = newUid(), updatedAt = if (seed) 0 else clock.millis(),
            ),
        )

    suspend fun update(task: Task) {
        val old = dao.byId(task.id) ?: return
        val changed = old.name != task.name || old.minutes != task.minutes || old.position != task.position || old.buffer != task.buffer
        // `checked` is this device's own business: ticking alone does not count as an edit
        dao.update(task.toEntity(old.uid, if (changed) clock.millis() else old.updatedAt))
    }

    suspend fun setChecked(id: Long, checked: Boolean) = dao.setChecked(id, checked)

    suspend fun delete(id: Long) {
        val old = dao.byId(id) ?: return
        dao.delete(id)
        if (old.updatedAt > 0) sync.putTombstone(TombstoneEntity(TombstoneEntity.TASK, old.uid, id, clock.millis()))
    }

    /** Re-inserts a removed task with its old id (undo): its routine memberships come back with it. */
    suspend fun restore(task: Task) {
        val tomb = sync.tombstoneOfLocal(TombstoneEntity.TASK, task.id)
        if (tomb != null) {
            sync.deleteTombstone(TombstoneEntity.TASK, tomb.key)
            dao.insert(task.toEntity(tomb.key, clock.millis()))
        } else {
            dao.insert(task.toEntity(newUid(), 0))
        }
    }

    suspend fun importAll(tasks: List<Task>) {
        dao.insertAll(tasks.mapIndexed { i, t -> t.toEntity().copy(id = 0, position = i) })
    }
}
