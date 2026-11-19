package com.ilsecondodasinistra.parakeet.domain

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/** A morning activity the user may do before leaving home. */
data class Task(
    val id: Long = 0,
    val name: String,
    val minutes: Int,
    val checked: Boolean,
    val position: Int = 0,
    /**
     * "Time for the unexpected": minutes of the plan that are not an activity. The wake-up is earlier by that
     * much, and after the alarm it is the cushion at the end of the ring, used up by anything that runs over.
     */
    val buffer: Boolean = false,
)

/** Result of the wake-up computation, shown on the main card. */
data class WakeUpPlan(
    val exitAt: LocalDateTime,
    val wakeAt: LocalDateTime,
    val totalMinutes: Int,
)

object WakeUpCalculator {

    /** Next moment at [exitTime]: today if still in the future, otherwise tomorrow. */
    fun nextOccurrence(exitTime: LocalTime, now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate().atTime(exitTime.withSecond(0).withNano(0))
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    fun totalMinutes(tasks: List<Task>): Int = tasks.filter { it.checked }.sumOf { it.minutes }

    /**
     * Plan for the next exit at [exitTime]. The exit is moved to tomorrow when the wake-up moment of
     * today's exit would already be in the past (an alarm can only ring in the future).
     */
    fun plan(exitTime: LocalTime, tasks: List<Task>, now: LocalDateTime): WakeUpPlan {
        val total = totalMinutes(tasks)
        var exitAt = nextOccurrence(exitTime, now)
        if (!exitAt.minusMinutes(total.toLong()).isAfter(now)) exitAt = exitAt.plusDays(1)
        return WakeUpPlan(exitAt, exitAt.minusMinutes(total.toLong()), total)
    }

    /** Minutes between two moments, never negative. */
    fun minutesBetween(from: LocalDateTime, to: LocalDateTime): Long =
        maxOf(0L, Duration.between(from, to).toMinutes())
}
