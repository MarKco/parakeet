package com.ilsecondodasinistra.parakeet

import com.ilsecondodasinistra.parakeet.domain.Routine
import com.ilsecondodasinistra.parakeet.domain.RoutineRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalTime

class RoutineRulesTest {

    private fun routine(id: Long, enabled: Boolean, vararg days: java.time.DayOfWeek) =
        Routine(id, "R$id", 0, LocalTime.of(8, 0), emptyList(), days.toSet(), enabled)

    @Test fun overlappingRoutineThatIsOnBlocks() {
        val others = listOf(routine(1, true, MONDAY, TUESDAY, FRIDAY), routine(2, true, SATURDAY))
        val c = RoutineRules.conflicts(3, setOf(MONDAY, FRIDAY, SATURDAY), others)
        assertEquals(listOf(1L, 2L), c.map { it.routine.id })
        assertEquals(setOf(MONDAY, FRIDAY), c[0].days)
        assertEquals(setOf(SATURDAY), c[1].days)
    }

    @Test fun aRoutineThatIsOffNeverBlocks() {
        assertTrue(RoutineRules.conflicts(3, setOf(MONDAY), listOf(routine(1, false, MONDAY))).isEmpty())
    }

    @Test fun theRoutineItselfIsIgnored() {
        assertTrue(RoutineRules.conflicts(1, setOf(MONDAY), listOf(routine(1, true, MONDAY))).isEmpty())
    }
}
