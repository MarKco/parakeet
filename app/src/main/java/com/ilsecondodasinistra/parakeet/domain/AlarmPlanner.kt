package com.ilsecondodasinistra.parakeet.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Routine palette (ARGB), harmonized with the Parakeet lime and the orange of the lettering. */
val RoutineColors: List<Int> = listOf(
    0xFFA9C607.toInt(), // lime
    0xFFF2A516.toInt(), // amber
    0xFF4FB3E8.toInt(), // sky
    0xFFEF6F6C.toInt(), // coral
    0xFFA58BE8.toInt(), // lavender
    0xFF2DB8A0.toInt(), // teal
)

/**
 * A weekly template: exit time + chosen activities, valid on [days].
 * A weekday belongs to at most one routine (enforced when saving).
 */
data class Routine(
    val id: Long = 0,
    val name: String,
    val color: Int,
    val exitTime: LocalTime,
    val taskIds: List<Long>, // in the order of the routine
    val days: Set<DayOfWeek> = emptySet(),
    val enabled: Boolean = true,
    val position: Int = 0,
)

/** One-time change for a single date: editing a day IS the override. */
sealed interface DayPlan {
    val date: LocalDate

    /** The alarm does not ring on [date]. */
    data class Skip(override val date: LocalDate) : DayPlan

    /** Another routine (or an extra alarm on a free day) for [date] only. */
    data class UseRoutine(override val date: LocalDate, val routineId: Long) : DayPlan

    /** Routine-based tweak for [date] only: own exit time and/or activities. */
    data class Custom(
        override val date: LocalDate,
        val routineId: Long?,
        val exitTime: LocalTime,
        val taskIds: List<Long>,
    ) : DayPlan
}

/** What happens on a given date, after applying weekly routines, overrides and pause. */
data class DaySchedule(
    val date: LocalDate,
    val routine: Routine?,          // routine in use (null for skipped/free days or custom without routine)
    val exitAt: LocalDateTime?,     // null = no alarm that day
    val wakeAt: LocalDateTime?,
    val totalMinutes: Int,
    val oneTime: Boolean,           // comes from a DayPlan, not from the weekly rule
    val skipped: Boolean,           // a weekly alarm exists but is skipped (override or pause)
    val taskIds: List<Long> = emptyList(), // activities of that day, in order (routine's, or the tweaked ones)
) {
    val rings: Boolean get() = wakeAt != null
}

object AlarmPlanner {

    /** Resolves [date] against weekly routines, one-time [overrides] and an optional pause (inclusive). */
    fun day(
        date: LocalDate,
        routines: List<Routine>,
        overrides: Map<LocalDate, DayPlan>,
        tasks: List<Task>,
        pausedUntil: LocalDate? = null,
    ): DaySchedule {
        val weekly = routines.firstOrNull { it.enabled && date.dayOfWeek in it.days }
        val override = overrides[date]
        if (pausedUntil != null && !date.isAfter(pausedUntil)) {
            return DaySchedule(date, weekly, null, null, 0, oneTime = false, skipped = weekly != null || override != null)
        }
        val minutesOf = { ids: List<Long> -> tasks.filter { it.id in ids }.sumOf { it.minutes } }
        return when (override) {
            is DayPlan.Skip -> DaySchedule(date, weekly, null, null, 0, oneTime = true, skipped = true)
            is DayPlan.UseRoutine -> routines.firstOrNull { it.id == override.routineId }
                ?.let { build(date, it, it.exitTime, it.taskIds, minutesOf(it.taskIds), oneTime = true) }
                ?: DaySchedule(date, null, null, null, 0, oneTime = true, skipped = false)
            is DayPlan.Custom -> build(
                date, routines.firstOrNull { it.id == override.routineId },
                override.exitTime, override.taskIds, minutesOf(override.taskIds), oneTime = true,
            )
            null -> weekly?.let { build(date, it, it.exitTime, it.taskIds, minutesOf(it.taskIds), oneTime = false) }
                ?: DaySchedule(date, null, null, null, 0, oneTime = false, skipped = false)
        }
    }

    /**
     * Next alarm strictly after [now], looking [horizonDays] ahead. Dates on or before [lastRungDate]
     * are excluded, so editing a day after its alarm rang never re-arms it.
     */
    fun next(
        now: LocalDateTime,
        routines: List<Routine>,
        overrides: Map<LocalDate, DayPlan>,
        tasks: List<Task>,
        pausedUntil: LocalDate? = null,
        lastRungDate: LocalDate? = null,
        horizonDays: Int = 400,
    ): DaySchedule? {
        val start = now.toLocalDate()
        for (i in 0..horizonDays) {
            val date = start.plusDays(i.toLong())
            if (lastRungDate != null && !date.isAfter(lastRungDate)) continue
            val d = day(date, routines, overrides, tasks, pausedUntil)
            if (d.wakeAt != null && d.wakeAt.isAfter(now)) return d
        }
        return null
    }

    /** Next [count] days from [from], for the date ribbon. */
    fun range(
        from: LocalDate, count: Int,
        routines: List<Routine>, overrides: Map<LocalDate, DayPlan>, tasks: List<Task>, pausedUntil: LocalDate? = null,
    ): List<DaySchedule> = (0 until count).map { day(from.plusDays(it.toLong()), routines, overrides, tasks, pausedUntil) }

    /** Weekly assignment: puts [days] on [routineId] and removes them from every other routine. */
    fun assignDays(routines: List<Routine>, routineId: Long, days: Set<DayOfWeek>): List<Routine> =
        routines.map { if (it.id == routineId) it.copy(days = days) else it.copy(days = it.days - days) }

    /** Auto-name from the days, until the user renames the routine. */
    fun suggestedName(days: Set<DayOfWeek>, weekdays: String, weekend: String, fallback: String): String = when (days) {
        DayOfWeek.entries.take(5).toSet() -> weekdays
        setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> weekend
        else -> fallback
    }

    private fun build(date: LocalDate, routine: Routine?, exit: LocalTime, taskIds: List<Long>, total: Int, oneTime: Boolean): DaySchedule {
        val exitAt = date.atTime(exit.withSecond(0).withNano(0))
        return DaySchedule(date, routine, exitAt, exitAt.minusMinutes(total.toLong()), total, oneTime, skipped = false, taskIds = taskIds)
    }
}
