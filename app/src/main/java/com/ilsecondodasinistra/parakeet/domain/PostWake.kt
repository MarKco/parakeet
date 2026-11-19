package com.ilsecondodasinistra.parakeet.domain

/**
 * What the user did to the task that was current at [PostWakeEvent.atMillis]. DEFER = "do it later": the task goes
 * to the back of the queue. SKIP = "skip for today": its unused time goes to the margin.
 */
enum class PostWakeAction { DONE, DEFER, SKIP }

data class PostWakeEvent(val taskId: Long, val action: PostWakeAction, val atMillis: Long)

enum class StepStatus {
    /** Ticked by the user. */
    DONE,

    /** Skipped for today. */
    SKIPPED,

    /** Put off with "do it later": this slot ended there and the task has another one at the back of the queue. */
    DEFERRED,
    CURRENT,
    UPCOMING,
}

/**
 * One slot of a task on the post-wake timeline. For [StepStatus.CURRENT] and [StepStatus.UPCOMING] the times are
 * projected. [plannedEndMillis] is where the slot ends if all goes as planned; [endMillis] is later than that for
 * a current task that is [overrun]. [colorIndex] is the position of the task in the original order, so its colour
 * never changes when it is postponed. [postponed] marks a later slot of a task that was put off before.
 * [beyondExit] is a task that, at this pace, no longer ends before the exit.
 */
data class StepState(
    val task: Task,
    val status: StepStatus,
    val startMillis: Long,
    val endMillis: Long,
    val plannedEndMillis: Long,
    val colorIndex: Int,
    val postponed: Boolean = false,
    val overrun: Boolean = false,
    val beyondExit: Boolean = false,
)

data class TimelineState(
    /** Every slot in time order, including the used part of postponed tasks: what the ring draws. */
    val segments: List<StepState>,
    /** One row per task, in the order of the latest slot of each (a postponed task sits at the back): the list. */
    val steps: List<StepState>,
    /** Index in [steps] of the task being done now, -1 when none (before the wake-up, or everything is resolved). */
    val currentIndex: Int,
    /** The one "contingency" block at the end of the ring: time left between the end of the last slot and the exit. */
    val marginMillis: Long,
    /** How far the end of the last slot is past the exit (the margin is used up and the plan no longer fits). */
    val overflowMillis: Long,
    val wakeAtMillis: Long,
    val exitAtMillis: Long,
    /** Where the plan starts: the moment the alarm was stopped. The time from the wake-up to here (a snooze) is already gone. */
    val startAtMillis: Long,
    val nowMillis: Long,
    val msPerMinute: Long,
    /** The "time for the unexpected" planned at the end of the morning (it is part of [marginMillis] from the start). */
    val bufferMillis: Long,
    /** Next instant at which the state changes on its own (the current task passes its time, the margin runs out, the exit), null after the exit. */
    val nextBoundaryMillis: Long?,
    /** How many events shaped this timeline: changes exactly when the user acts, which is when the ring animates. */
    val eventCount: Int,
) {
    val exitDue: Boolean get() = nowMillis >= exitAtMillis
    val allResolved: Boolean get() = steps.none { it.status == StepStatus.CURRENT || it.status == StepStatus.UPCOMING }
    val current: StepState? get() = steps.getOrNull(currentIndex)
    val remainingToExitMillis: Long get() = (exitAtMillis - nowMillis).coerceAtLeast(0)

    /** What was gained on the plan: the margin above the planned cushion. */
    val gainedMillis: Long get() = (marginMillis - bufferMillis).coerceAtLeast(0)

    /** Rounded down: the margin is never overstated. */
    val marginMinutes: Int get() = (marginMillis / msPerMinute).toInt()

    /** Rounded up: lateness is never understated. */
    val overflowMinutes: Int get() = ((overflowMillis + msPerMinute - 1) / msPerMinute).toInt()

    /** The current task is taking longer than planned and is eating into the margin. */
    val marginEroding: Boolean get() = current?.overrun == true && marginMillis > 0

    /** "Do it later" only makes sense while another task waits behind the current one. */
    val canDefer: Boolean
        get() = currentIndex >= 0 && steps.drop(currentIndex + 1).any { it.status == StepStatus.UPCOMING }

    val canSkip: Boolean get() = currentIndex >= 0
}

/**
 * The morning after the alarm: tasks laid end to end from the moment the alarm was stopped, the whole ring being
 * wake-up → exit (the minutes between the scheduled wake-up and the stop, a snooze, show as time already gone). The cursor is the real clock; nothing moves on by itself.
 *
 * - The current task stays current until the user acts. Past its planned time its slot keeps growing with the
 *   clock ([StepState.overrun]), pushing the later slots forward and eating the margin.
 * - "Done" or "Skip" end the slot right there: the time not used moves to the margin, one block at the end.
 * - The "time for the unexpected" is not a task of the queue: [tasks] leaves it out and [bufferMillis] says how
 *   much of the end of the ring it is. It is just margin from the start, consumed by running over without any alert.
 * - "Do it later" ends the slot there too and queues the task again at the back, with the minutes it still
 *   needed (at least [DEFER_MIN_MINUTES], or its whole duration if that is shorter).
 * - Once the end of the last slot passes the exit, the plan no longer fits: [TimelineState.overflowMillis].
 *
 * Pure function of the event log, so it can be recomputed after a process death.
 */
object PostWakeTimeline {

    const val MINUTE_MILLIS = 60_000L
    const val DEFER_MIN_MINUTES = 5

    private data class Slot(val colorIndex: Int, val task: Task, val budgetMillis: Long, val postponed: Boolean)

    fun compute(
        wakeAtMillis: Long,
        exitAtMillis: Long,
        tasks: List<Task>,
        events: List<PostWakeEvent>,
        nowMillis: Long,
        msPerMinute: Long = MINUTE_MILLIS,
        bufferMillis: Long = 0,
        startAtMillis: Long = wakeAtMillis,
    ): TimelineState {
        // The plan starts when the alarm was stopped, not at the scheduled wake-up: a snooze in between is time lost
        // from the cushion (and from the lead, then into lateness), the first activity keeps all its minutes.
        val planStart = maxOf(wakeAtMillis, minOf(startAtMillis, nowMillis))
        val segments = ArrayList<StepState>(tasks.size)
        val queue = ArrayDeque(tasks.mapIndexed { i, t -> Slot(i, t, t.minutes * msPerMinute, false) })
        val consumed = HashSet<Int>() // an event ends exactly one slot, never the one it just opened
        var cursor = planStart
        var currentFound = false
        while (queue.isNotEmpty()) {
            val slot = queue.removeFirst()
            val task = slot.task
            val start = cursor
            val plannedEnd = start + slot.budgetMillis
            val reached = !currentFound && start <= nowMillis
            val eventIndex = if (reached) {
                events.indices
                    .filter { it !in consumed && events[it].taskId == task.id && events[it].atMillis in start..nowMillis }
                    .minByOrNull { events[it].atMillis }
            } else null
            val event = eventIndex?.let { events[it] }
            if (eventIndex != null) consumed += eventIndex
            val state = when {
                event != null -> {
                    val status = when (event.action) {
                        PostWakeAction.DONE -> StepStatus.DONE
                        PostWakeAction.SKIP -> StepStatus.SKIPPED
                        PostWakeAction.DEFER -> StepStatus.DEFERRED
                    }
                    if (event.action == PostWakeAction.DEFER) {
                        val remaining = (plannedEnd - event.atMillis).coerceAtLeast(0)
                        val minimum = minOf(DEFER_MIN_MINUTES * msPerMinute, task.minutes * msPerMinute)
                        queue.addLast(slot.copy(budgetMillis = maxOf(remaining, minimum), postponed = true))
                    }
                    StepState(task, status, start, event.atMillis, plannedEnd, slot.colorIndex, slot.postponed)
                }
                reached -> {
                    currentFound = true
                    StepState(
                        task, StepStatus.CURRENT, start, maxOf(plannedEnd, nowMillis), plannedEnd, slot.colorIndex,
                        slot.postponed, overrun = nowMillis > plannedEnd,
                    )
                }
                else -> StepState(task, StepStatus.UPCOMING, start, plannedEnd, plannedEnd, slot.colorIndex, slot.postponed)
            }
            val open = state.status == StepStatus.CURRENT || state.status == StepStatus.UPCOMING
            val flagged = if (open && state.endMillis > exitAtMillis) state.copy(beyondExit = true) else state
            segments += flagged
            cursor = flagged.endMillis
        }
        // The list shows each task once, at its latest slot
        val lastSlot = HashMap<Long, Int>()
        segments.forEachIndexed { i, s -> lastSlot[s.task.id] = i }
        val steps = segments.filterIndexed { i, s -> lastSlot[s.task.id] == i }
        val currentIndex = steps.indexOfFirst { it.status == StepStatus.CURRENT }
        val current = steps.getOrNull(currentIndex)
        val marginMillis = (exitAtMillis - cursor).coerceAtLeast(0)
        val boundary = when {
            nowMillis >= exitAtMillis -> null
            else -> listOfNotNull(
                current?.plannedEndMillis,
                // the moment the margin runs out if the current activity keeps going: from then on it is lateness
                current?.takeIf { marginMillis > 0 }?.let { maxOf(it.plannedEndMillis, nowMillis) + marginMillis },
                // before the wake-up nothing runs yet: the first task starts at the wake-up
                wakeAtMillis.takeIf { currentIndex == -1 && it > nowMillis },
                exitAtMillis,
            ).filter { it > nowMillis }.minOrNull()
        }
        return TimelineState(
            segments = segments,
            steps = steps,
            currentIndex = currentIndex,
            marginMillis = marginMillis,
            overflowMillis = (cursor - exitAtMillis).coerceAtLeast(0),
            wakeAtMillis = wakeAtMillis,
            exitAtMillis = exitAtMillis,
            startAtMillis = planStart,
            nowMillis = nowMillis,
            msPerMinute = msPerMinute,
            bufferMillis = bufferMillis,
            nextBoundaryMillis = boundary,
            eventCount = consumed.size,
        )
    }

    /** `taskId:D|L|S:millis` pairs joined by `;`, for the DataStore (D = done, L = later, S = skip). */
    fun encode(events: List<PostWakeEvent>): String =
        events.joinToString(";") {
            val code = when (it.action) {
                PostWakeAction.DONE -> 'D'
                PostWakeAction.DEFER -> 'L'
                PostWakeAction.SKIP -> 'S'
            }
            "${it.taskId}:$code:${it.atMillis}"
        }

    fun decode(raw: String?): List<PostWakeEvent> = raw.orEmpty().split(';').mapNotNull { part ->
        val f = part.split(':')
        if (f.size != 3) return@mapNotNull null
        val id = f[0].toLongOrNull() ?: return@mapNotNull null
        val at = f[2].toLongOrNull() ?: return@mapNotNull null
        val action = when (f[1]) {
            "D" -> PostWakeAction.DONE
            "L" -> PostWakeAction.DEFER
            "S" -> PostWakeAction.SKIP
            else -> return@mapNotNull null
        }
        PostWakeEvent(id, action, at)
    }
}
