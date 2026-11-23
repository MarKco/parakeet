package com.ilsecondodasinistra.parakeet

import com.ilsecondodasinistra.parakeet.data.toDays
import com.ilsecondodasinistra.parakeet.data.toMask
import com.ilsecondodasinistra.parakeet.domain.AlarmPlanner
import com.ilsecondodasinistra.parakeet.domain.Routine
import com.ilsecondodasinistra.parakeet.domain.Task
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class PlanMappingTest {

    @Test fun daysMaskRoundTrip() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY)
        assertEquals(0b1000101, days.toMask())
        assertEquals(days, days.toMask().toDays())
        assertEquals(DayOfWeek.entries.toSet(), 0b1111111.toDays())
    }

    @Test fun exitAfterMidnightWakesTheEveningBefore() {
        val wed = LocalDate.of(2026, 11, 18)
        val r = Routine(1, "Notte", 0, LocalTime.of(0, 0), listOf(1), setOf(DayOfWeek.WEDNESDAY))
        val next = AlarmPlanner.next(wed.minusDays(1).atTime(23, 0), listOf(r), emptyMap(), listOf(Task(1, "Doccia", 45, true)))!!
        assertEquals(wed, next.date)
        assertEquals(wed.minusDays(1).atTime(23, 15), next.wakeAt)
    }

    @Test fun suggestedNames() {
        val weekdays = DayOfWeek.entries.take(5).toSet()
        assertEquals("Feriali", AlarmPlanner.suggestedName(weekdays, "Feriali", "Weekend", "Routine 2"))
        assertEquals("Weekend", AlarmPlanner.suggestedName(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), "Feriali", "Weekend", "Routine 2"))
        assertEquals("Routine 2", AlarmPlanner.suggestedName(setOf(DayOfWeek.MONDAY), "Feriali", "Weekend", "Routine 2"))
    }
}
