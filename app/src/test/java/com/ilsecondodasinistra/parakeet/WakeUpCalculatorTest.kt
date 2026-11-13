package com.ilsecondodasinistra.parakeet

import com.ilsecondodasinistra.parakeet.domain.Task
import com.ilsecondodasinistra.parakeet.domain.WakeUpCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class WakeUpCalculatorTest {

    private fun task(minutes: Int, checked: Boolean = true) = Task(name = "t", minutes = minutes, checked = checked)

    @Test fun exitLaterToday_staysToday() {
        val now = LocalDateTime.of(2026, 11, 13, 7, 0)
        assertEquals(LocalDateTime.of(2026, 11, 13, 8, 30), WakeUpCalculator.nextOccurrence(LocalTime.of(8, 30), now))
    }

    @Test fun exitAlreadyPassed_movesToTomorrow() {
        val now = LocalDateTime.of(2026, 11, 13, 22, 0)
        assertEquals(LocalDateTime.of(2026, 11, 14, 8, 0), WakeUpCalculator.nextOccurrence(LocalTime.of(8, 0), now))
    }

    @Test fun exitExactlyNow_movesToTomorrow() {
        val now = LocalDateTime.of(2026, 11, 13, 8, 0, 30)
        assertEquals(LocalDateTime.of(2026, 11, 14, 8, 0), WakeUpCalculator.nextOccurrence(LocalTime.of(8, 0), now))
    }

    @Test fun monthEnd_rollsOver() {
        val now = LocalDateTime.of(2026, 11, 30, 23, 0)
        assertEquals(LocalDateTime.of(2026, 12, 1, 8, 0), WakeUpCalculator.nextOccurrence(LocalTime.of(8, 0), now))
    }

    @Test fun onlyCheckedTasksCount() {
        assertEquals(35, WakeUpCalculator.totalMinutes(listOf(task(20), task(15), task(99, checked = false))))
    }

    @Test fun wakeUp_isExitMinusTotal() {
        val now = LocalDateTime.of(2026, 11, 13, 22, 0)
        val plan = WakeUpCalculator.plan(LocalTime.of(8, 0), listOf(task(20), task(55)), now)
        assertEquals(LocalDateTime.of(2026, 11, 14, 8, 0), plan.exitAt)
        assertEquals(LocalDateTime.of(2026, 11, 14, 6, 45), plan.wakeAt)
        assertEquals(75, plan.totalMinutes)
    }

    @Test fun noTasks_wakeEqualsExit_whenInFuture() {
        val now = LocalDateTime.of(2026, 11, 13, 22, 0)
        val plan = WakeUpCalculator.plan(LocalTime.of(8, 0), emptyList(), now)
        assertEquals(plan.exitAt, plan.wakeAt)
    }

    @Test fun wakeInThePast_movesPlanToTomorrow() {
        // 7:30 now, leaving at 8:00 with 90 min of prep: today's wake-up (6:30) already passed
        val now = LocalDateTime.of(2026, 11, 13, 7, 30)
        val plan = WakeUpCalculator.plan(LocalTime.of(8, 0), listOf(task(90)), now)
        assertEquals(LocalDateTime.of(2026, 11, 14, 8, 0), plan.exitAt)
        assertEquals(LocalDateTime.of(2026, 11, 14, 6, 30), plan.wakeAt)
    }

    @Test fun wakeCanCrossMidnight() {
        val now = LocalDateTime.of(2026, 11, 13, 20, 0)
        val plan = WakeUpCalculator.plan(LocalTime.of(0, 30), listOf(task(60)), now)
        assertEquals(LocalDateTime.of(2026, 11, 13, 23, 30), plan.wakeAt)
    }
}
