package com.ilsecondodasinistra.parakeet.domain

/** What the user did to the task that was current at [atMillis]. */
enum class PostWakeAction { DONE, SKIP }

data class PostWakeEvent(val taskId: Long, val action: PostWakeAction, val atMillis: Long)

enum class StepStatus {
    /** Ticked by the user. */
    DONE,

    /** Passed over with "Next": not done. */
    SKIPPED,

    /** Its time ran out with no action: the timeline moved on by itself. */
    EXPIRED,
    CURRENT,
    UPCOMING,
}

/** One task on the post-wake timeline. For [StepStatus.CURRENT] and [StepStatus.UPCOMING] the times are projected. */
data class StepState(
    val task: Task,
    val status: StepStatus,
    val startMillis: Long,
    val endMillis: Long,
)

data class TimelineState(
    val steps: List<StepState>,
    /** Index of the task being done now, -1 when none (before the wake-up, or everything is resolved). */
    val currentIndex: Int,
    /** Time left between the end of the last task and the exit, gained by finishing early. */
    val marginMillis: Long,
    val wakeAtMillis: Long,
    val exitAtMillis: Long,
    val nowMillis: Long,
    val msPerMinute: Long,
    /** Next instant at which the state changes on its own (a task runs out, the exit), null once the exit has passed. */
    val nextBoundaryMillis: Long?,
) {
    val exitDue: Boolean get() = nowMillis >= exitAtMillis
    val allResolved: Boolean get() = steps.none { it.status == StepStatus.CURRENT || it.status == StepStatus.UPCOMING }
    val current: StepState? get() = steps.getOrNull(currentIndex)
    val remainingToExitMillis: Long get() = (exitAtMillis - nowMillis).coerceAtLeast(0)
    val marginMinutes: Int get() = (marginMillis / msPerMinute).toInt()
}

/**
 * The morning after the alarm: tasks laid end to end from the scheduled wake-up. "Done" or "Next" pressed
 * while a task is current ends it right there and the following one starts at once (the time saved becomes
 * margin before the exit). A task whose time runs out with no action ends by itself, as [StepStatus.EXPIRED].
 * Pure function of the event log, so it can be recomputed after a process death.
 */
object PostWakeTimeline {

    const val MINUTE_MILLIS = 60_000L

    fun compute(
        wakeAtMillis: Long,
        exitAtMillis: Long,
        tasks: List<Task>,
        events: List<PostWakeEvent>,
        nowMillis: Long,
        msPerMinute: Long = MINUTE_MILLIS,
    ): TimelineState {
        val steps = ArrayList<StepState>(tasks.size)
        var cursor = wakeAtMillis
        var currentIndex = -1
        var currentEnd: Long? = null
        for ((i, task) in tasks.withIndex()) {
            val start = cursor
            val nominalEnd = start + task.minutes * msPerMinute
            val reached = currentIndex == -1 && start <= nowMillis
            val event = if (reached) {
                events.filter { it.taskId == task.id && it.atMillis in start until nominalEnd && it.atMillis <= nowMillis }
                    .minByOrNull { it.atMillis }
            } else null
            val state = when {
                event != null -> StepState(
                    task, if (event.action == PostWakeAction.DONE) StepStatus.DONE else StepStatus.SKIPPED,
                    start, event.atMillis,
                )
                reached && nominalEnd <= nowMillis -> StepState(task, StepStatus.EXPIRED, start, nominalEnd)
                reached -> {
                    currentIndex = i
                    currentEnd = nominalEnd
                    StepState(task, StepStatus.CURRENT, start, nominalEnd)
                }
                else -> StepState(task, StepStatus.UPCOMING, start, nominalEnd)
            }
            steps += state
            cursor = state.endMillis
        }
        val boundary = when {
            nowMillis >= exitAtMillis -> null
            else -> listOfNotNull(
                currentEnd,
                // before the wake-up nothing runs yet: the first task starts at the wake-up
                wakeAtMillis.takeIf { currentIndex == -1 && it > nowMillis },
                exitAtMillis,
            ).filter { it > nowMillis }.minOrNull()
        }
        return TimelineState(
            steps = steps,
            currentIndex = currentIndex,
            marginMillis = (exitAtMillis - cursor).coerceAtLeast(0),
            wakeAtMillis = wakeAtMillis,
            exitAtMillis = exitAtMillis,
            nowMillis = nowMillis,
            msPerMinute = msPerMinute,
            nextBoundaryMillis = boundary,
        )
    }

    /** `taskId:D|S:millis` pairs joined by `;`, for the DataStore. */
    fun encode(events: List<PostWakeEvent>): String =
        events.joinToString(";") { "${it.taskId}:${if (it.action == PostWakeAction.DONE) 'D' else 'S'}:${it.atMillis}" }

    fun decode(raw: String?): List<PostWakeEvent> = raw.orEmpty().split(';').mapNotNull { part ->
        val f = part.split(':')
        if (f.size != 3) return@mapNotNull null
        val id = f[0].toLongOrNull() ?: return@mapNotNull null
        val at = f[2].toLongOrNull() ?: return@mapNotNull null
        val action = when (f[1]) {
            "D" -> PostWakeAction.DONE
            "S" -> PostWakeAction.SKIP
            else -> return@mapNotNull null
        }
        PostWakeEvent(id, action, at)
    }
}
