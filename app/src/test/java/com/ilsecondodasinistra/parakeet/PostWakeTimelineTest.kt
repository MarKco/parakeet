package com.ilsecondodasinistra.parakeet

import com.ilsecondodasinistra.parakeet.domain.PostWakeAction.DONE
import com.ilsecondodasinistra.parakeet.domain.PostWakeAction.SKIP
import com.ilsecondodasinistra.parakeet.domain.PostWakeEvent
import com.ilsecondodasinistra.parakeet.domain.PostWakeTimeline
import com.ilsecondodasinistra.parakeet.domain.StepStatus
import com.ilsecondodasinistra.parakeet.domain.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class PostWakeTimelineTest {

    // Doccia 15 + Colazione 20 + Vestirsi 10 = 45 min: wake 07:00, exit 07:45, Tuesday 17 Nov 2026
    private val tasks = listOf(Task(1, "Doccia", 15, true), Task(2, "Colazione", 20, true), Task(3, "Vestirsi", 10, true))
    private val wake = at(7, 0)
    private val exit = at(7, 45)

    private fun at(h: Int, m: Int, s: Int = 0): Long =
        LocalDateTime.of(2026, 11, 17, h, m, s).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun compute(now: Long, vararg events: PostWakeEvent) =
        PostWakeTimeline.compute(wake, exit, tasks, events.toList(), now)

    private fun statuses(now: Long, vararg events: PostWakeEvent) = compute(now, *events).steps.map { it.status }

    @Test fun noEventsFollowsTheClock() {
        val t = compute(at(7, 20))
        assertEquals(listOf(StepStatus.EXPIRED, StepStatus.CURRENT, StepStatus.UPCOMING), t.steps.map { it.status })
        assertEquals(1, t.currentIndex)
        assertEquals(at(7, 35), t.steps[1].endMillis)
        assertEquals(0L, t.marginMillis)
        assertEquals(at(7, 35), t.nextBoundaryMillis)
    }

    @Test fun beforeWakeNothingIsCurrent() {
        val t = compute(at(6, 50))
        assertEquals(-1, t.currentIndex)
        assertEquals(List(3) { StepStatus.UPCOMING }, t.steps.map { it.status })
        assertEquals(wake, t.nextBoundaryMillis)
    }

    @Test fun doneEarlyStartsNextAtOnceAndBuildsMargin() {
        val t = compute(at(7, 8), PostWakeEvent(1, DONE, at(7, 5)))
        assertEquals(StepStatus.DONE, t.steps[0].status)
        assertEquals(at(7, 5), t.steps[1].startMillis)
        assertEquals(at(7, 25), t.steps[1].endMillis)
        assertEquals(1, t.currentIndex)
        assertEquals(10 * 60_000L, t.marginMillis)
    }

    @Test fun skipIsNotDoneButAlsoAdvances() {
        val t = compute(at(7, 8), PostWakeEvent(1, SKIP, at(7, 2)))
        assertEquals(StepStatus.SKIPPED, t.steps[0].status)
        assertEquals(StepStatus.CURRENT, t.steps[1].status)
        assertEquals(at(7, 2), t.steps[1].startMillis)
    }

    @Test fun expiredTaskAdvancesByItself() {
        val t = compute(at(7, 36))
        assertEquals(listOf(StepStatus.EXPIRED, StepStatus.EXPIRED, StepStatus.CURRENT), t.steps.map { it.status })
        assertEquals(2, t.currentIndex)
        assertEquals(exit, t.nextBoundaryMillis)
    }

    @Test fun snoozedWakeLosesTheFirstSlots() {
        // alarm stopped at 07:18: the first task's slot is already gone, only the clock matters
        assertEquals(listOf(StepStatus.EXPIRED, StepStatus.CURRENT, StepStatus.UPCOMING), statuses(at(7, 18)))
    }

    @Test fun doneTwiceOrOutOfTurnIsIgnored() {
        // "done" of task 2 while task 1 is current, and a second "done" on task 1: only the first counts
        val t = compute(
            at(7, 10),
            PostWakeEvent(2, DONE, at(7, 3)),
            PostWakeEvent(1, DONE, at(7, 4)),
            PostWakeEvent(1, DONE, at(7, 9)),
        )
        assertEquals(StepStatus.DONE, t.steps[0].status)
        assertEquals(at(7, 4), t.steps[0].endMillis)
        assertEquals(StepStatus.CURRENT, t.steps[1].status)
    }

    @Test fun lateEventAfterTheSlotIsIgnored() {
        val t = compute(at(7, 20), PostWakeEvent(1, DONE, at(7, 16)))
        assertEquals(StepStatus.EXPIRED, t.steps[0].status)
    }

    @Test fun everythingDoneEarlyLeavesAllMargin() {
        val t = compute(
            at(7, 20),
            PostWakeEvent(1, DONE, at(7, 5)),
            PostWakeEvent(2, DONE, at(7, 12)),
            PostWakeEvent(3, DONE, at(7, 15)),
        )
        assertTrue(t.allResolved)
        assertEquals(-1, t.currentIndex)
        assertEquals(30 * 60_000L, t.marginMillis)
        assertEquals(exit, t.nextBoundaryMillis)
        assertFalse(t.exitDue)
    }

    @Test fun afterTheExitNothingIsPending() {
        val t = compute(at(7, 50))
        assertTrue(t.exitDue)
        assertTrue(t.allResolved)
        assertNull(t.nextBoundaryMillis)
        assertEquals(0L, t.remainingToExitMillis)
    }

    @Test fun demoScaleSpeedsUpTheClock() {
        // 1 minute = 5 seconds: the first task (15 min) lasts 75 s
        val t = PostWakeTimeline.compute(wake, wake + 45 * 5_000, tasks, emptyList(), wake + 80_000, msPerMinute = 5_000)
        assertEquals(StepStatus.EXPIRED, t.steps[0].status)
        assertEquals(StepStatus.CURRENT, t.steps[1].status)
    }

    @Test fun eventsRoundTripThroughTheStringFormat() {
        val events = listOf(PostWakeEvent(1, DONE, 1000L), PostWakeEvent(7, SKIP, 2000L))
        assertEquals(events, PostWakeTimeline.decode(PostWakeTimeline.encode(events)))
        assertTrue(PostWakeTimeline.decode(null).isEmpty())
        assertTrue(PostWakeTimeline.decode("garbage;1:X:5;a:D:1").isEmpty())
    }
}
