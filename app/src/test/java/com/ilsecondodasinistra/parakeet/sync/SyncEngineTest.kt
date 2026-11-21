package com.ilsecondodasinistra.parakeet.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEngineTest {
    private val today = 20000L
    private val now = 1_700_000_000_000L

    private suspend fun round(folder: FakeFolder, store: FakeStore, id: String, at: Long = now, readOwn: Boolean = false) =
        SyncEngine.run(folder, store, id, "Dev $id", at, today, readOwn)

    @Test fun twoDevicesConverge() = runTest {
        val folder = FakeFolder()
        val a = FakeStore(state(task("t1", "Doccia", 100)))
        val b = FakeStore(state(task("t2", "Caffè", 100)))
        assertTrue(round(folder, a, "A").wrote)
        val rb = round(folder, b, "B")
        assertEquals(listOf("Dev A"), rb.devices)
        assertEquals(1, rb.changed)
        round(folder, a, "A")
        assertEquals(setOf("t1", "t2"), a.state.tasks.keys)
        assertEquals(a.state, b.state)
    }

    @Test fun nothingChangedMeansNothingWritten() = runTest {
        val folder = FakeFolder()
        val a = FakeStore(state(task("t1", at = 100)))
        round(folder, a, "A")
        val text = folder.files["parakeet-A.json"]
        val again = round(folder, a, "A", at = now + 99999)
        assertFalse(again.wrote)
        assertEquals(text, folder.files["parakeet-A.json"])
    }

    @Test fun aDeletionTravels() = runTest {
        val folder = FakeFolder()
        val a = FakeStore(state(task("t1", at = 100)))
        val b = FakeStore()
        round(folder, a, "A"); round(folder, b, "B")
        assertEquals(1, b.state.tasks.size)
        a.state = state(task("t1", "", now - 1000, deleted = true))
        round(folder, a, "A"); round(folder, b, "B")
        assertTrue(b.state.tasks["t1"]!!.deleted)
    }

    @Test fun unreadableAndNewerFilesAreSkippedAndCounted() = runTest {
        val folder = FakeFolder()
        folder.files["parakeet-X.json"] = "{ not json"
        folder.files["parakeet-Y.json"] = SyncEngine.encode(SyncFile(version = 99, deviceId = "Y", state = state(task("t9", at = 1))))
        folder.files["notes.txt"] = "hello"
        val a = FakeStore(state(task("t1", at = 100)))
        val r = round(folder, a, "A")
        assertTrue(r.ok)
        assertEquals(2, r.unreadable)
        assertEquals(setOf("t1"), a.state.tasks.keys)
    }

    @Test fun conflictCopyIsMergedThenArchivedThenDeleted() = runTest {
        val folder = FakeFolder()
        val a = FakeStore(state(task("t1", at = 100)))
        round(folder, a, "A")
        val copy = "parakeet-B.sync-conflict-20261121-120000-ZZZ.json"
        folder.files[copy] = SyncEngine.encode(SyncFile(deviceId = "B", deviceName = "Dev B", state = state(task("t2", at = 200))))
        val r = round(folder, a, "A")
        assertEquals(1, r.resolved)
        assertEquals(setOf("t1", "t2"), a.state.tasks.keys)
        assertFalse(copy in folder.files)
        val archived = folder.files.keys.single { SyncNames.isResolved(it) }
        // not read again
        a.state = a.state.copy(tasks = emptyMap()).also { }
        val again = round(folder, FakeStore(), "C")
        assertEquals(0, again.resolved)
        // a month later it is gone
        val later = SyncEngine.run(folder, FakeStore(), "A", "Dev A", now, today + 31)
        assertTrue(later.ok)
        assertFalse(archived in folder.files)
    }

    @Test fun conflictCopyStaysWhenItCannotBeRenamed() = runTest {
        val folder = FakeFolder().apply { failRename = true }
        folder.files["parakeet-B.sync-conflict-20261121-120000-ZZZ.json"] = SyncEngine.encode(SyncFile(deviceId = "B", state = state(task("t2", at = 200))))
        val a = FakeStore()
        val r = round(folder, a, "A")
        assertEquals(0, r.resolved)
        assertTrue(a.state.tasks.containsKey("t2"))
        assertEquals(1, folder.files.keys.count { SyncNames.isConflict(it) })
    }

    @Test fun ownOldFileIsReadOnlyWhenAsked() = runTest {
        val folder = FakeFolder()
        folder.files["parakeet-A.json"] = SyncEngine.encode(SyncFile(deviceId = "A", state = state(task("t1", at = 100))))
        val fresh = FakeStore()
        assertTrue(round(folder, fresh, "A").ok)
        // not read: the fresh device overwrote its own file with nothing
        assertEquals(emptySet<String>(), fresh.state.tasks.keys)
        folder.files["parakeet-A.json"] = SyncEngine.encode(SyncFile(deviceId = "A", state = state(task("t1", at = 100))))
        val back = FakeStore()
        round(folder, back, "A", readOwn = true)
        assertEquals(setOf("t1"), back.state.tasks.keys)
    }

    @Test fun deviceSettingsAreWrittenButNeverMerged() = runTest {
        val folder = FakeFolder()
        val a = FakeStore(device = DeviceSettings(mapOf("alarm_mode" to "INTERNAL")))
        round(folder, a, "A")
        val seen = SyncEngine.devices(folder)
        assertEquals("INTERNAL", seen.single().device.values["alarm_mode"])
        val b = FakeStore()
        round(folder, b, "B")
        assertTrue(b.device.values.isEmpty())
        // changing only a phone setting rewrites the file
        a.device = DeviceSettings(mapOf("alarm_mode" to "CLOCK"))
        assertTrue(round(folder, a, "A").wrote)
    }

    @Test fun pastDayPlansOfOthersAreIgnored() = runTest {
        val folder = FakeFolder()
        folder.files["parakeet-B.json"] = SyncEngine.encode(
            SyncFile(deviceId = "B", state = state(plans = listOf(plan(today - 5, "r", 100), plan(today, "r", 100)))),
        )
        val a = FakeStore()
        round(folder, a, "A")
        assertEquals(setOf(today.toString()), a.state.dayPlans.keys)
    }
}
