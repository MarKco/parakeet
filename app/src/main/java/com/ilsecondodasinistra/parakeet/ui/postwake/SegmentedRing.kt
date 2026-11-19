package com.ilsecondodasinistra.parakeet.ui.postwake

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.util.lerp
import com.ilsecondodasinistra.parakeet.domain.RoutineColors
import com.ilsecondodasinistra.parakeet.domain.TimelineState
import kotlin.math.cos
import kotlin.math.sin

/** Colour of the step at [index] in the routine order: the fixed routine palette, in rotation. */
fun stepColor(index: Int): Color = Color(RoutineColors[index % RoutineColors.size])

/** The contingency block: a quiet grey that does not compete with the activities. */
val MarginColor = Color(0xFF70756A)

/** Used for lateness: the coral of the routine palette. */
val LateColor = Color(RoutineColors[3])

/** Used while the margin is being eaten: the amber of the routine palette. */
val ErodingColor = Color(RoutineColors[1])

/**
 * The time from the wake-up to the exit as a ring that never changes scale, one coloured arc per activity and one
 * grey arc of margin at the end. The elapsed part fades to a faint track, so the ring is consumed counterclockwise
 * (as system timers do) and a dot marks "now", which is the real clock. An activity that runs over grows with the
 * clock, pushing the later arcs and shrinking the margin; past the margin, a thin coral arc outside the ring says
 * how late the plan now ends. When the user acts, the time not used by the finished activity slides into the
 * margin, and a postponed activity's arc travels to the back.
 */
@Composable
fun SegmentedRing(state: TimelineState, modifier: Modifier = Modifier, dotColor: Color = Color.White, center: @Composable BoxScope.() -> Unit) {
    val arcs = arcsOf(state)
    val animator = remember { ArcAnimator(arcs, state.eventCount) }
    // After this composition: a change of layout caused by the user (an event) starts a slide
    SideEffect { animator.submit(arcs, state.eventCount) }
    LaunchedEffect(animator.epoch) { if (animator.epoch > 0) animator.run() }

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            // Mirrored: like a timer, the ring is used up counterclockwise, the remaining arc shrinking toward the exit at 12 o'clock
            scale(scaleX = -1f, scaleY = 1f, pivot = Offset(size.width / 2, size.height / 2)) {
                val stroke = size.minDimension * 0.075f
                val thin = size.minDimension * 0.016f // the lateness arc, outside the ring
                val inset = thin * 2.6f + stroke / 2
                val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
                val topLeft = Offset(inset, inset)
                val span = (state.exitAtMillis - state.wakeAtMillis).coerceAtLeast(1L)
                val now = ((state.nowMillis - state.wakeAtMillis).coerceIn(0L, span)).toFloat() / span * 360f

                fun arc(color: Color, from: Float, to: Float) {
                    if (to - from <= 0.05f) return
                    drawArc(color, -90f + from, to - from, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                }

                fun segment(color: Color, start: Float, end: Float) {
                    val gap = if (end - start > GAP_DEG * 3) GAP_DEG / 2 else 0f
                    val a0 = start + gap
                    val a1 = end - gap
                    arc(color.copy(alpha = TRACK_ALPHA), a0, a1)
                    arc(color, maxOf(a0, now), a1)
                }

                // The minutes lost to a snooze, between the scheduled wake-up and the stop: consumed, in the ink's own tone
                val lostEnd = ((state.startAtMillis - state.wakeAtMillis).coerceIn(0L, span)).toFloat() / span * 360f
                arc(dotColor.copy(alpha = 0.14f), 0f, lostEnd)

                animator.shown().forEach { a ->
                    segment(a.color ?: if (a.colorIndex < 0) MarginColor else stepColor(a.colorIndex), a.start, a.end)
                }

                // Lateness: how far past the exit the plan now ends, as a thin arc around the ring
                if (state.overflowMillis > 0) {
                    val sweep = (state.overflowMillis.toFloat() / span * 360f).coerceIn(2f, 360f)
                    drawArc(
                        LateColor, -90f, sweep, false, Offset(thin / 2, thin / 2),
                        Size(size.width - thin, size.height - thin), style = Stroke(thin, cap = StrokeCap.Round),
                    )
                }

                // "Now" dot on the leading edge of the ring
                if (state.nowMillis in state.wakeAtMillis until state.exitAtMillis) {
                    val r = (size.minDimension - 2 * inset) / 2
                    val rad = Math.toRadians((now - 90f).toDouble())
                    drawCircle(
                        dotColor, radius = stroke * 0.42f,
                        center = Offset(size.width / 2 + r * cos(rad).toFloat(), size.height / 2 + r * sin(rad).toFloat()),
                    )
                }
            }
        }
        center()
    }
}

/** An arc on the ring, in degrees from the top. [colorIndex] -1 is the margin. [key] is "taskId#slot". */
private data class ArcSpec(
    val key: String,
    val colorIndex: Int,
    val start: Float,
    val end: Float,
    val color: Color? = null,
)

private data class Span(val start: Float, val end: Float)

/** The time a finished activity did not use, on its way to the margin. */
private data class Carry(val from: Span, val to: Span, val color: Color)

private const val MARGIN_KEY = "margin"
private const val CARRY_KEY = "carry"

/** Every slot of the timeline as an arc, then the margin up to the exit. */
private fun arcsOf(state: TimelineState): List<ArcSpec> {
    val span = (state.exitAtMillis - state.wakeAtMillis).coerceAtLeast(1L)
    fun angle(t: Long): Float = (t - state.wakeAtMillis).coerceIn(0L, span).toFloat() / span * 360f
    val slotOf = HashMap<Long, Int>()
    val arcs = state.segments.map { s ->
        val slot = slotOf[s.task.id] ?: 0
        slotOf[s.task.id] = slot + 1
        ArcSpec("${s.task.id}#$slot", s.colorIndex, angle(s.startMillis), angle(s.endMillis))
    }
    val lastEnd = state.segments.lastOrNull()?.endMillis ?: state.wakeAtMillis
    return arcs + ArcSpec(MARGIN_KEY, -1, angle(lastEnd), 360f)
}

/**
 * Slides the arcs from where they are on screen to the new layout when the user acts. Between actions the layout
 * only follows the clock (an overrunning activity growing), so it is drawn as it is, with no animation lag.
 *
 * - An arc that did not exist before (the new slot of a postponed task) starts from where the same task's previous
 *   slot was, so it appears to travel to the back.
 * - When a finished activity frees time, that time is carried, still in the activity's colour, to the end of the
 *   ring, where it becomes margin.
 */
private class ArcAnimator(initial: List<ArcSpec>, private var events: Int) {
    private var target: List<ArcSpec> = initial
    private var from: Map<String, Span> = emptyMap()
    private var carry: Carry? = null

    // State, not Animatable: it is written together with the new layout, so no frame ever shows it half-switched
    private var progress by mutableFloatStateOf(1f)

    /** Counts started slides; the composable runs the animation when it changes. */
    var epoch by mutableIntStateOf(0)
        private set

    /** The arcs as they should be drawn right now. Reads the animation state, so drawing follows each frame. */
    fun shown(): List<ArcSpec> {
        val p = progress
        if (p >= 1f) return target
        val arcs = target.map { a ->
            val f = from[a.key] ?: return@map a
            a.copy(start = lerp(f.start, a.start, p), end = lerp(f.end, a.end, p))
        }
        val c = carry ?: return arcs
        return arcs + ArcSpec(
            CARRY_KEY, -2, lerp(c.from.start, c.to.start, p), lerp(c.from.end, c.to.end, p),
            color = androidx.compose.ui.graphics.lerp(c.color, MarginColor, p),
        )
    }

    fun submit(next: List<ArcSpec>, eventCount: Int) {
        if (eventCount == events) {
            target = next
            return
        }
        val onScreen = shown().filter { it.key != CARRY_KEY }.associateBy { it.key }
        fun spanOf(key: String) = onScreen[key]?.let { Span(it.start, it.end) }
        from = next.associate { a -> a.key to (spanOf(a.key) ?: spanOf(previousSlotKey(a.key)) ?: Span(a.start, a.end)) }
        carry = carryOf(onScreen, next)
        target = next
        events = eventCount
        progress = 0f
        epoch++
    }

    suspend fun run() {
        animate(0f, 1f, animationSpec = tween(durationMillis = SLIDE_MILLIS, easing = FastOutSlowInEasing)) { value, _ -> progress = value }
        carry = null
    }

    /** The piece an activity gave up (its old end past its new end) travels to the part the margin just gained. */
    private fun carryOf(onScreen: Map<String, ArcSpec>, next: List<ArcSpec>): Carry? {
        val oldMargin = onScreen[MARGIN_KEY] ?: return null
        val newMargin = next.firstOrNull { it.key == MARGIN_KEY } ?: return null
        if (newMargin.start >= oldMargin.start - 0.1f) return null
        val shrunk = next.firstOrNull { a ->
            a.key != MARGIN_KEY && (onScreen[a.key]?.end ?: Float.NEGATIVE_INFINITY) > a.end + 0.1f
        } ?: return null
        val old = onScreen.getValue(shrunk.key)
        return Carry(
            from = Span(shrunk.end, old.end),
            to = Span(newMargin.start, oldMargin.start),
            color = stepColor(shrunk.colorIndex),
        )
    }

    private fun previousSlotKey(key: String): String {
        val i = key.lastIndexOf('#')
        val slot = key.substring(i + 1).toIntOrNull() ?: return key
        return if (slot > 0) "${key.substring(0, i)}#${slot - 1}" else key
    }
}

/** The ring slides, and the list rows travel for as long. */
const val SLIDE_MILLIS = 700

private const val GAP_DEG = 3f
private const val TRACK_ALPHA = 0.13f
