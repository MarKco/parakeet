package com.ilsecondodasinistra.parakeet

import com.ilsecondodasinistra.parakeet.domain.PostWakeAction.DEFER
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
    private val minute = 60_000L

    private fun at(h: Int, m: Int, s: Int = 0): Long =
        LocalDateTime.of(2026, 11, 17, h, m, s).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun compute(now: Long, vararg events: PostWakeEvent) =
        PostWakeTimeline.compute(wake, exit, tasks, events.toList(), now)

    @Test fun onTimeThereIsNoMarginAndNoLateness() {
        val t = compute(at(7, 10))
        assertEquals(listOf(StepStatus.CURRENT, StepStatus.UPCOMING, StepStatus.UPCOMING), t.steps.map { it.status })
        assertEquals(0L, t.marginMillis)
        assertEquals(0L, t.overflowMillis)
        assertFalse(t.steps[0].overrun)
        assertEquals(at(7, 15), t.nextBoundaryMillis) // the current task passes its time
    }

    @Test fun nothingMovesOnByItself() {
        // 07:20: Doccia was planned until 07:15 but nobody said "done": it is still current and keeps growing
        val t = compute(at(7, 20))
        assertEquals(StepStatus.CURRENT, t.steps[0].status)
        assertTrue(t.steps[0].overrun)
        assertEquals(wake, t.steps[0].startMillis)
        assertEquals(at(7, 20), t.steps[0].endMillis)
        assertEquals(at(7, 15), t.steps[0].plannedEndMillis)
        // the later slots are pushed forward and the last one no longer ends before the exit
        assertEquals(at(7, 20), t.steps[1].startMillis)
        assertEquals(at(7, 50), t.steps[2].endMillis)
        assertTrue(t.steps[2].beyondExit)
        assertFalse(t.steps[1].beyondExit)
        assertEquals(5 * minute, t.overflowMillis)
        assertEquals(0L, t.marginMillis)
        assertEquals(exit, t.nextBoundaryMillis) // past its time there is nothing left to wait for but the exit
    }

    @Test fun beforeWakeNothingIsCurrent() {
        val t = compute(at(6, 50))
        assertEquals(-1, t.currentIndex)
        assertEquals(List(3) { StepStatus.UPCOMING }, t.steps.map { it.status })
        assertEquals(wake, t.nextBoundaryMillis)
        assertFalse(t.canSkip)
    }

    @Test fun doneEarlyMovesTheUnusedTimeToTheMargin() {
        val t = compute(at(7, 8), PostWakeEvent(1, DONE, at(7, 5)))
        assertEquals(StepStatus.DONE, t.steps[0].status)
        assertEquals(at(7, 5), t.steps[0].endMillis)
        assertEquals(at(7, 5), t.steps[1].startMillis)
        assertEquals(at(7, 25), t.steps[1].endMillis)
        assertEquals(10 * minute, t.marginMillis)
        assertEquals(10, t.marginMinutes)
        assertEquals(0L, t.overflowMillis)
        assertEquals(1, t.eventCount)
    }

    @Test fun skipFreesTheTimeLikeDoneButIsNotDone() {
        val t = compute(at(7, 8), PostWakeEvent(1, SKIP, at(7, 5)))
        assertEquals(StepStatus.SKIPPED, t.steps[0].status)
        assertEquals(StepStatus.CURRENT, t.steps[1].status)
        assertEquals(10 * minute, t.marginMillis)
    }

    @Test fun runningLateEatsTheMarginBeforeItBecomesLateness() {
        // Doccia done at 07:05 leaves 10 min of margin; Colazione (until 07:25) is still going at 07:30
        val t = compute(at(7, 30), PostWakeEvent(1, DONE, at(7, 5)))
        assertTrue(t.steps[1].overrun)
        assertEquals(at(7, 30), t.steps[1].endMillis)
        assertEquals(at(7, 40), t.steps[2].endMillis)
        assertEquals(5 * minute, t.marginMillis)
        assertEquals(0L, t.overflowMillis)
        assertTrue(t.marginEroding)
        assertFalse(t.steps[2].beyondExit)
    }

    @Test fun onceTheMarginIsGoneItIsLateness() {
        val t = compute(at(7, 40), PostWakeEvent(1, DONE, at(7, 5)))
        assertEquals(0L, t.marginMillis)
        assertEquals(5 * minute, t.overflowMillis)
        assertEquals(5, t.overflowMinutes)
        assertTrue(t.steps[2].beyondExit)
        assertFalse(t.marginEroding)
    }

    @Test fun deferKeepsTheMinutesStillNeeded() {
        // Doccia deferred at 07:05 after 5 of its 15 min: 10 min are queued at the back
        val t = compute(at(7, 8), PostWakeEvent(1, DEFER, at(7, 5)))
        assertEquals(listOf("Doccia", "Colazione", "Vestirsi", "Doccia"), t.segments.map { it.task.name })
        assertEquals(StepStatus.DEFERRED, t.segments[0].status)
        assertEquals(at(7, 5), t.segments[0].endMillis)
        assertEquals(listOf("Colazione", "Vestirsi", "Doccia"), t.steps.map { it.task.name })
        assertEquals(0, t.currentIndex)
        val later = t.steps[2]
        assertTrue(later.postponed)
        assertEquals(at(7, 35), later.startMillis)
        assertEquals(exit, later.endMillis)
        assertEquals(0L, t.marginMillis)
        assertEquals(0L, t.overflowMillis) // the scale never changes
    }

    @Test fun deferKeepsTheColourOfTheOriginalPosition() {
        val t = compute(at(7, 8), PostWakeEvent(1, DEFER, at(7, 5)))
        assertEquals(listOf(0, 1, 2, 0), t.segments.map { it.colorIndex })
    }

    @Test fun deferNeverQueuesLessThanFiveMinutes() {
        // 13 of 15 min used: 2 would be left, 5 are queued
        val short = compute(at(7, 14), PostWakeEvent(1, DEFER, at(7, 13)))
        assertEquals(5 * minute, short.steps.last().let { it.endMillis - it.startMillis })
    }

    @Test fun deferAfterOverrunQueuesFiveMinutesAndAddsToTheLateness() {
        // Doccia (15 min) deferred at 07:20: nothing was left, 5 min are queued behind the 30 of the others
        val t = compute(at(7, 22), PostWakeEvent(1, DEFER, at(7, 20)))
        assertEquals(listOf("Colazione", "Vestirsi", "Doccia"), t.steps.map { it.task.name })
        val later = t.steps.last()
        assertEquals(at(7, 50), later.startMillis)
        assertEquals(at(7, 55), later.endMillis)
        assertEquals(10 * minute, t.overflowMillis)
    }

    @Test fun aShortTaskIsNotStretchedToTheMinimum() {
        val quick = listOf(Task(1, "Vitamine", 3, true), Task(2, "Colazione", 20, true))
        val t = PostWakeTimeline.compute(wake, wake + 23 * minute, quick, listOf(PostWakeEvent(1, DEFER, wake)), wake + minute)
        assertEquals(3 * minute, t.steps.last().let { it.endMillis - it.startMillis })
    }

    @Test fun deferAfterEarlyFinishStillFitsInFull() {
        // Doccia done at 07:05 (10 min of margin), Colazione deferred at 07:10 after 5 of 20 min: 15 come back
        val t = compute(at(7, 12), PostWakeEvent(1, DONE, at(7, 5)), PostWakeEvent(2, DEFER, at(7, 10)))
        assertEquals(listOf("Doccia", "Colazione", "Vestirsi", "Colazione"), t.segments.map { it.task.name })
        val later = t.steps.last()
        assertEquals(at(7, 20), later.startMillis)
        assertEquals(at(7, 35), later.endMillis)
        assertEquals(10 * minute, t.marginMillis)
    }

    @Test fun aTaskCanBePutOffMoreThanOnce() {
        val t = compute(at(7, 12), PostWakeEvent(1, DEFER, at(7, 5)), PostWakeEvent(2, DEFER, at(7, 10)))
        assertEquals(listOf("Vestirsi", "Doccia", "Colazione"), t.steps.map { it.task.name })
        assertEquals(StepStatus.CURRENT, t.steps[0].status)
        assertEquals(2, t.eventCount)
    }

    @Test fun anEventNeverEndsTheSlotItOpened() {
        // "later" on the last task: it gets a second slot and the same event must not end that one too
        val t = compute(
            at(7, 25),
            PostWakeEvent(1, DONE, at(7, 5)), PostWakeEvent(2, DONE, at(7, 20)), PostWakeEvent(3, DEFER, at(7, 22)),
        )
        assertEquals(2, t.segments.count { it.task.id == 3L })
        assertEquals(StepStatus.CURRENT, t.steps.last().status)
        assertEquals(at(7, 22), t.steps.last().startMillis)
    }

    @Test fun deferAndSkipOnlyWhenTheyMakeSense() {
        assertTrue(compute(at(7, 10)).canDefer) // others wait behind Doccia
        assertTrue(compute(at(7, 10)).canSkip)
        val last = compute(at(7, 30), PostWakeEvent(1, DONE, at(7, 5)), PostWakeEvent(2, DONE, at(7, 25)))
        assertEquals(StepStatus.CURRENT, last.current?.status)
        assertFalse(last.canDefer) // Vestirsi is the last one
        assertTrue(last.canSkip)
    }

    @Test fun eventsOutOfTurnAreIgnored() {
        // "done" of Colazione while Doccia is current, and a second "done" on Doccia: only the first on Doccia counts
        val t = compute(
            at(7, 10),
            PostWakeEvent(2, DONE, at(7, 3)),
            PostWakeEvent(1, DONE, at(7, 4)),
            PostWakeEvent(1, DONE, at(7, 9)),
        )
        assertEquals(StepStatus.DONE, t.steps[0].status)
        assertEquals(at(7, 4), t.steps[0].endMillis)
        assertEquals(StepStatus.CURRENT, t.steps[1].status)
        assertEquals(1, t.eventCount)
    }

    @Test fun everythingDoneEarlyLeavesAllTheMarginAtTheEnd() {
        val t = compute(
            at(7, 20),
            PostWakeEvent(1, DONE, at(7, 5)), PostWakeEvent(2, DONE, at(7, 12)), PostWakeEvent(3, DONE, at(7, 15)),
        )
        assertTrue(t.allResolved)
        assertEquals(-1, t.currentIndex)
        assertEquals(30 * minute, t.marginMillis)
        assertEquals(exit, t.nextBoundaryMillis)
        assertFalse(t.exitDue)
        assertFalse(t.canSkip)
    }

    @Test fun afterTheExitOnlyTheClockIsLeft() {
        val t = compute(at(7, 50))
        assertTrue(t.exitDue)
        assertNull(t.nextBoundaryMillis)
        assertEquals(0L, t.remainingToExitMillis)
    }

    @Test fun demoScaleSpeedsUpTheClock() {
        // 1 minute = 5 seconds: Doccia (15 min) is planned for 75 s, so at 80 s it has run over
        val t = PostWakeTimeline.compute(wake, wake + 45 * 5_000, tasks, emptyList(), wake + 80_000, msPerMinute = 5_000)
        assertEquals(StepStatus.CURRENT, t.steps[0].status)
        assertTrue(t.steps[0].overrun)
        assertEquals(1, t.overflowMinutes) // 5 s late = under one demo minute, rounded up
    }

    @Test fun eventsRoundTripThroughTheStringFormat() {
        val events = listOf(PostWakeEvent(1, DONE, 1000L), PostWakeEvent(7, DEFER, 2000L), PostWakeEvent(9, SKIP, 3000L))
        assertEquals(events, PostWakeTimeline.decode(PostWakeTimeline.encode(events)))
        assertTrue(PostWakeTimeline.decode(null).isEmpty())
        assertTrue(PostWakeTimeline.decode("garbage;1:X:5;a:D:1").isEmpty())
    }
}
