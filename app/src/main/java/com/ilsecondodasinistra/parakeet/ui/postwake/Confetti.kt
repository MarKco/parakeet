package com.ilsecondodasinistra.parakeet.ui.postwake

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.withFrameNanos
import com.ilsecondodasinistra.parakeet.domain.RoutineColors
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Piece(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var rotation: Float, val spin: Float, val width: Float, val height: Float, val color: Color,
)

/**
 * A burst of confetti from [originInRoot] (a point in root coordinates, e.g. the middle of the ring) each time
 * [burst] changes to a new value above zero. Drawn over everything, takes no touches.
 */
@Composable
fun ConfettiBurst(burst: Int, originInRoot: Offset, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val pieces = remember { ArrayList<Piece>() }
    var frame by remember { mutableLongStateOf(0L) }
    var age by remember { mutableFloatStateOf(0f) }
    var layoutOrigin by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(burst) {
        if (burst == 0) return@LaunchedEffect
        val random = Random(burst)
        val origin = originInRoot - layoutOrigin
        pieces.clear()
        repeat(PIECES) {
            // Out in every direction, a little more upward, then gravity pulls them down
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val speed = (260f + random.nextFloat() * 720f) * density
            pieces += Piece(
                x = origin.x, y = origin.y,
                vx = cos(angle) * speed, vy = sin(angle) * speed - 340f * density,
                rotation = random.nextFloat() * 360f, spin = (random.nextFloat() - 0.5f) * 900f,
                width = (5f + random.nextFloat() * 6f) * density, height = (8f + random.nextFloat() * 8f) * density,
                color = Color(RoutineColors[random.nextInt(RoutineColors.size)]),
            )
        }
        age = 0f
        var last = withFrameNanos { it }
        while (age < LIFE_SECONDS) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            age += dt
            val drag = 1f - 1.6f * dt
            for (p in pieces) {
                p.vy += GRAVITY * density * dt
                p.vx *= drag
                p.vy *= drag
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.rotation += p.spin * dt
            }
            frame++
        }
        pieces.clear()
        frame++
    }

    Canvas(
        modifier.fillMaxSize().onGloballyPositioned { layoutOrigin = it.positionInRoot() },
    ) {
        frame // read, so each step of the simulation redraws
        val fade = ((LIFE_SECONDS - age) / FADE_SECONDS).coerceIn(0f, 1f)
        for (p in pieces) {
            rotate(p.rotation, Offset(p.x, p.y)) {
                drawRect(p.color.copy(alpha = fade), Offset(p.x - p.width / 2, p.y - p.height / 2), Size(p.width, p.height))
            }
        }
    }
}

private const val PIECES = 110
private const val GRAVITY = 1500f
private const val LIFE_SECONDS = 3f
private const val FADE_SECONDS = 0.8f
