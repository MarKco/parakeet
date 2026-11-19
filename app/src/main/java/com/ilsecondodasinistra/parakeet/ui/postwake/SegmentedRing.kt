package com.ilsecondodasinistra.parakeet.ui.postwake

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import com.ilsecondodasinistra.parakeet.domain.RoutineColors
import com.ilsecondodasinistra.parakeet.domain.TimelineState
import kotlin.math.cos
import kotlin.math.sin

/** Colour of the step at [index] in the routine order: the fixed routine palette, in rotation. */
fun stepColor(index: Int): Color = Color(RoutineColors[index % RoutineColors.size])

/**
 * The time from the wake-up to the exit as a ring, one coloured arc per activity. The elapsed part fades to a
 * faint track, so the ring is consumed counterclockwise (as system timers do) and a dot marks "now". The time saved by finishing early is
 * the grey arc before the exit.
 */
@Composable
fun SegmentedRing(state: TimelineState, modifier: Modifier = Modifier, center: @Composable BoxScope.() -> Unit) {
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            // Mirrored: like a timer, the ring is used up counterclockwise, the remaining arc shrinking toward the exit at 12 o'clock
            scale(scaleX = -1f, scaleY = 1f, pivot = Offset(size.width / 2, size.height / 2)) {
            val stroke = size.minDimension * 0.075f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            val span = (state.exitAtMillis - state.wakeAtMillis).coerceAtLeast(1L)
            fun angle(t: Long): Float = (t - state.wakeAtMillis).coerceIn(0L, span).toFloat() / span * 360f
            val now = angle(state.nowMillis)

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

            state.steps.forEachIndexed { i, step ->
                segment(stepColor(i), angle(step.startMillis), angle(step.endMillis))
            }
            // Margin: from the end of the last activity to the exit
            val lastEnd = state.steps.lastOrNull()?.endMillis ?: state.wakeAtMillis
            segment(Color(0xFF8A8F80), angle(lastEnd), 360f)

            // "Now" dot on the leading edge of the ring
            if (state.nowMillis in state.wakeAtMillis until state.exitAtMillis) {
                val r = (size.minDimension - stroke) / 2
                val rad = Math.toRadians((now - 90f).toDouble())
                drawCircle(
                    Color.White, radius = stroke * 0.42f,
                    center = Offset(size.width / 2 + r * cos(rad).toFloat(), size.height / 2 + r * sin(rad).toFloat()),
                )
            }
            }
        }
        center()
    }
}

private const val GAP_DEG = 3f
private const val TRACK_ALPHA = 0.13f
