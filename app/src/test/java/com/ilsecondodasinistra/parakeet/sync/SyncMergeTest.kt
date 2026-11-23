package com.ilsecondodasinistra.parakeet.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {
    private val a = state(task("t1", "Doccia", 100), task("t2", "Caffè", 100), routines = listOf(routine("r1", 0b11, 100, listOf("t1"))))
    private val b = state(task("t1", "Doccia lunga", 200), task("t3", "Barba", 150), plans = listOf(plan(20000, "r1", 120)))

    @Test fun newestEditWinsPerEntry() {
        val m = a.mergedWith(b)
        assertEquals("Doccia lunga", m.tasks["t1"]!!.name)
        assertEquals(setOf("t1", "t2", "t3"), m.tasks.keys)
        assertEquals(1, m.routines.size)
        assertEquals(1, m.dayPlans.size)
    }

    @Test fun mergeIsIdempotentAndOrderIndependent() {
        assertEquals(a.mergedWith(b), b.mergedWith(a))
        assertEquals(a.mergedWith(b), a.mergedWith(b).mergedWith(b))
        assertEquals(a.mergedWith(b), a.mergedWith(b).mergedWith(a))
    }

    @Test fun tieKeepsTheLocalOne() {
        val x = state(task("t1", "locale", 100))
        val y = state(task("t1", "altro", 100))
        assertEquals("locale", x.mergedWith(y).tasks["t1"]!!.name)
    }

    @Test fun tombstoneBeatsOlderEntryAndNewerEntryRevives() {
        val dead = state(task("t1", "", 300, deleted = true))
        assertTrue(a.mergedWith(dead).tasks["t1"]!!.deleted)
        assertTrue(dead.mergedWith(a).tasks["t1"]!!.deleted)
        val revived = state(task("t1", "Doccia", 400))
        assertEquals(false, dead.mergedWith(revived).tasks["t1"]!!.deleted)
    }

    @Test fun settingsMergeByStamp() {
        val x = SyncState(settings = mapOf("snooze" to StampedValue("5", 100), "vibrate" to StampedValue("true", 50)))
        val y = SyncState(settings = mapOf("snooze" to StampedValue("15", 200)))
        val m = x.mergedWith(y)
        assertEquals("15", m.settings["snooze"]!!.value)
        assertEquals("true", m.settings["vibrate"]!!.value)
    }

    @Test fun aWeekdayBelongsToTheMostRecentRoutine() {
        val s = state(routines = listOf(routine("old", 0b0000111, 100), routine("new", 0b0000110, 200)))
        val fixed = s.withExclusiveDays()
        assertEquals(0b0000001, fixed.routines["old"]!!.daysMask)
        assertEquals(0b0000110, fixed.routines["new"]!!.daysMask)
        // nothing is stamped
        assertEquals(100, fixed.routines["old"]!!.updatedAt)
        assertEquals(fixed, fixed.withExclusiveDays())
    }

    @Test fun aRoutineThatIsOffKeepsItsDays() {
        val s = state(routines = listOf(routine("off", 0b0000111, 100, enabled = false), routine("on", 0b0000110, 200)))
        val fixed = s.withExclusiveDays()
        assertEquals(0b0000111, fixed.routines["off"]!!.daysMask)
        assertEquals(0b0000110, fixed.routines["on"]!!.daysMask)
        // and an older routine that is on still loses to a newer one that is on, whatever an off one holds
        val three = state(routines = listOf(routine("off", 0b1, 300, enabled = false), routine("old", 0b1, 100), routine("new", 0b1, 200)))
        assertEquals(0, three.withExclusiveDays().routines["old"]!!.daysMask)
        assertEquals(0b1, three.withExclusiveDays().routines["off"]!!.daysMask)
    }

    @Test fun deletedRoutinesDoNotHoldDays() {
        val s = state(routines = listOf(routine("gone", 0b1, 300, deleted = true), routine("live", 0b1, 100)))
        assertEquals(0b1, s.withExclusiveDays().routines["live"]!!.daysMask)
    }

    @Test fun pastDayPlansAndOldTombstonesAreDropped() {
        val now = 2_000_000_000_000L
        val s = state(
            task("old", "", 1, deleted = true), task("fresh", "x", now),
            plans = listOf(plan(19999, "r", now), plan(20000, "r", now), plan(20001, "r", now)),
        )
        val p = s.pruned(todayEpochDay = 20001, nowMillis = now)
        assertEquals(setOf("fresh"), p.tasks.keys)
        assertEquals(setOf("20000", "20001"), p.dayPlans.keys)
    }

    @Test fun namesOfConflictCopies() {
        assertEquals("abc", SyncNames.deviceIdOf("parakeet-abc.sync-conflict-20261121-120000-XYZ.json"))
        assertTrue(SyncNames.isConflict("parakeet-abc.sync-conflict-20261121-120000-XYZ.json"))
        val r = SyncNames.resolvedName("parakeet-abc.sync-conflict-20261121-120000-XYZ.json", "20261122")
        assertEquals("parakeet-abc.resolved-20261122-20261121-120000-XYZ.json", r)
        assertTrue(!SyncNames.isConflict(r) && SyncNames.isResolved(r))
        assertEquals("20261122", SyncNames.resolvedDate(r))
    }
}
