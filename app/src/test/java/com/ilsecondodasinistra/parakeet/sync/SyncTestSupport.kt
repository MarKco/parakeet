package com.ilsecondodasinistra.parakeet.sync

/** In-memory shared folder. */
class FakeFolder : SyncFolder {
    val files = linkedMapOf<String, String>()
    var failRename = false
    override fun list() = files.keys.toList()
    override fun read(name: String) = files[name]
    override fun write(name: String, text: String) { files[name] = text }
    override fun rename(name: String, newName: String) {
        if (failRename) error("no rename")
        files[newName] = files.remove(name) ?: error("missing $name")
    }
    override fun delete(name: String) { files.remove(name) }
}

/** A device's data: the state it would share. */
class FakeStore(var state: SyncState = SyncState(), var device: DeviceSettings = DeviceSettings()) : SyncStore {
    var applied = 0
    override suspend fun snapshot() = state
    override suspend fun apply(merged: SyncState): Int {
        applied++
        var n = 0
        n += merged.tasks.count { state.tasks[it.key] != it.value }
        n += merged.routines.count { state.routines[it.key] != it.value }
        n += merged.dayPlans.count { state.dayPlans[it.key] != it.value }
        n += merged.settings.count { state.settings[it.key] != it.value }
        state = merged
        return n
    }
    override suspend fun deviceSettings() = device
}

fun task(uid: String, name: String = uid, at: Long, deleted: Boolean = false) = TaskRec(uid, name, 10, false, 0, at, deleted)
fun routine(uid: String, mask: Int = 0, at: Long, tasks: List<String> = emptyList(), deleted: Boolean = false, enabled: Boolean = true) =
    RoutineRec(uid, uid, 0, 480, mask, enabled, 0, tasks, at, deleted)
fun plan(day: Long, routineUid: String?, at: Long, deleted: Boolean = false) =
    DayPlanRec(day, "ROUTINE", routineUid, null, emptyList(), at, deleted)
fun state(vararg tasks: TaskRec, routines: List<RoutineRec> = emptyList(), plans: List<DayPlanRec> = emptyList(), settings: Map<String, StampedValue> = emptyMap()) =
    SyncState(tasks.associateBy { it.uid }, routines.associateBy { it.uid }, plans.associateBy { it.epochDay.toString() }, settings)
