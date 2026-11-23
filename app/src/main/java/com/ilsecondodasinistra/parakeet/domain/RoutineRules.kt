package com.ilsecondodasinistra.parakeet.domain

import java.time.DayOfWeek

/** Weekdays that [routine] (switched on) would share with another routine that is already on. */
data class DayConflict(val routine: Routine, val days: Set<DayOfWeek>)

/**
 * Two routines that are ON never cover the same weekday. A routine that is off may keep its days (so the user can
 * switch between "workdays" and "holidays" without re-assigning them), but cannot be switched on over a day that
 * another routine on already covers.
 */
object RoutineRules {

    /** Routines that are on and cover some of [days]; [id] (the routine being switched on) is ignored. */
    fun conflicts(id: Long, days: Set<DayOfWeek>, routines: List<Routine>): List<DayConflict> =
        routines.filter { it.id != id && it.enabled }.mapNotNull { r ->
            (r.days intersect days).takeIf { it.isNotEmpty() }?.let { DayConflict(r, it) }
        }
}
