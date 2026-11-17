package com.ilsecondodasinistra.parakeet

import com.ilsecondodasinistra.parakeet.domain.AlarmPlanner
import com.ilsecondodasinistra.parakeet.domain.DayPlan
import com.ilsecondodasinistra.parakeet.domain.Routine
import com.ilsecondodasinistra.parakeet.domain.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AlarmPlannerTest {

    private val tasks = listOf(Task(1, "Doccia", 15, true), Task(2, "Colazione", 20, true), Task(3, "Palestra", 45, false))
    private val work = Routine(1, "Lavoro", 0, LocalTime.of(7, 40), setOf(1, 2), setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY))
    private val weekend = Routine(2, "Weekend", 0, LocalTime.of(9, 30), setOf(2), setOf(SATURDAY, SUNDAY), enabled = false)
    private val routines = listOf(work, weekend)

    // Monday 17 Nov 2026, 21:00
    private val mondayEvening = LocalDateTime.of(2026, 11, 17, 21, 0).minusDays(1)
    private val tue = LocalDate.of(2026, 11, 17)

    @Test fun nextIsTomorrowFromWeeklyRoutine() {
        val next = AlarmPlanner.next(mondayEvening, routines, emptyMap(), tasks)!!
        assertEquals(tue.atTime(7, 5), next.wakeAt)
        assertEquals("Lavoro", next.routine?.name)
    }

    @Test fun skipMovesToFollowingDay() {
        val next = AlarmPlanner.next(mondayEvening, routines, mapOf(tue to DayPlan.Skip(tue)), tasks)!!
        assertEquals(tue.plusDays(1), next.date)
    }

    @Test fun customTweakOnlyForThatDate() {
        val custom = DayPlan.Custom(tue, 1, LocalTime.of(8, 0), setOf(1, 3))
        val next = AlarmPlanner.next(mondayEvening, routines, mapOf(tue to custom), tasks)!!
        assertEquals(tue.atTime(7, 0), next.wakeAt)
        assertTrue(next.oneTime)
        assertEquals(tue.plusDays(1).atTime(7, 5), AlarmPlanner.day(tue.plusDays(1), routines, mapOf(tue to custom), tasks).wakeAt)
    }

    @Test fun extraAlarmOnFreeDayWithDisabledRoutine() {
        val sat = LocalDate.of(2026, 11, 21)
        val d = AlarmPlanner.day(sat, routines, mapOf(sat to DayPlan.UseRoutine(sat, 2)), tasks)
        assertEquals(sat.atTime(9, 10), d.wakeAt)
        assertNull(AlarmPlanner.day(sat, routines, emptyMap(), tasks).wakeAt)
    }

    @Test fun pauseSkipsDaysInclusive() {
        val next = AlarmPlanner.next(mondayEvening, routines, emptyMap(), tasks, pausedUntil = tue.plusDays(2))!!
        assertEquals(tue.plusDays(3), next.date)
    }

    @Test fun alreadyRungDateIsNeverReArmed() {
        val morning = tue.atTime(7, 10)
        val next = AlarmPlanner.next(morning, routines, mapOf(tue to DayPlan.Custom(tue, 1, LocalTime.of(10, 0), setOf(1))), tasks, lastRungDate = tue)!!
        assertEquals(tue.plusDays(1), next.date)
    }

    @Test fun oneShotWithoutDaysRingsOnceThenNothing() {
        val solo = Routine(5, "Solo", 0, LocalTime.of(8, 0), setOf(1))
        val overrides = mapOf(tue to DayPlan.UseRoutine(tue, 5))
        assertEquals(tue, AlarmPlanner.next(mondayEvening, listOf(solo), overrides, tasks)!!.date)
        assertNull(AlarmPlanner.next(tue.atTime(9, 0), listOf(solo), overrides, tasks))
    }

    @Test fun assignDaysIsExclusive() {
        val updated = AlarmPlanner.assignDays(routines, 2, setOf(FRIDAY, SATURDAY))
        assertEquals(setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY), updated.first { it.id == 1L }.days)
        assertEquals(setOf(FRIDAY, SATURDAY), updated.first { it.id == 2L }.days)
    }
}
