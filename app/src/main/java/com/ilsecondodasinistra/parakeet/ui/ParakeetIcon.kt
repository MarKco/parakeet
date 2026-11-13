package com.ilsecondodasinistra.parakeet.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.random.Random

private val Dark = Color(0xFF324511)
private val Lime = Color(0xFFA9C607)
private val Hand = Color(0xFF4A5925)

/**
 * The Parakeet alarm clock drawn in code (same geometry as docs/brand/parakeet_icon.svg, 512 units),
 * so that its parts can move: [leftEarLift]/[rightEarLift] raise the "bells" (0..1),
 * [handsSpin] rotates the hands (degrees, minute hand; hour hand moves 1/12).
 */
@Composable
fun ParakeetIcon(
    modifier: Modifier = Modifier,
    leftEarLift: Float = 0f,
    rightEarLift: Float = 0f,
    handsSpin: Float = 0f,
) {
    Canvas(modifier) {
        val s = size.minDimension / 512f
        scale(s, s, pivot = Offset.Zero) {
            drawParakeet(leftEarLift, rightEarLift, handsSpin)
        }
    }
}

private fun DrawScope.drawParakeet(leftLift: Float, rightLift: Float, spin: Float) {
    val body = Offset(259f, 270f)
    // Ears ("bells"), cut by a gap around the body
    val gap = Path().apply { addOval(androidx.compose.ui.geometry.Rect(body, 262f)) }
    clipPath(gap, clipOp = ClipOp.Difference) {
        translate(top = -26f * leftLift) {
            rotate(-10f * leftLift, pivot = Offset(78f, 76f)) { drawCircle(Dark, 72f, Offset(78f, 76f)) }
        }
        translate(top = -26f * rightLift) {
            rotate(10f * rightLift, pivot = Offset(438f, 76f)) { drawCircle(Dark, 72f, Offset(438f, 76f)) }
        }
    }
    drawCircle(Dark, 241f, body)
    val center = Offset(260f, 272f)
    drawCircle(Lime, 166f, center)
    // Hands
    rotate(spin / 12f, pivot = center) {
        drawLine(Hand, center, Offset(158f, 226f), strokeWidth = 22f, cap = StrokeCap.Butt)
    }
    rotate(spin, pivot = center) {
        drawLine(Hand, center, Offset(358f, 172f), strokeWidth = 22f, cap = StrokeCap.Butt)
    }
    drawCircle(Dark, 27f, center)
    // Gloss
    drawOval(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.95f),
            0.55f to Color.White.copy(alpha = 0.45f),
            1f to Color.White.copy(alpha = 0f),
            startY = 55f, endY = 249f,
        ),
        topLeft = Offset(90f, 55f),
        size = Size(310f, 194f),
    )
}

/** Animations the icon can play; one is picked at random for every user action. */
enum class IconAnimation { Shake, RingBells, Jump, SpinHands }

/**
 * Parakeet icon that plays a random animation every time [trigger] changes (ignores the initial value).
 * Animations are skipped when the system "Remove animations" setting is on.
 */
@Composable
fun AnimatedParakeetIcon(trigger: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val rotation = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val scaleX = remember { Animatable(1f) }
    val scaleY = remember { Animatable(1f) }
    val leftEar = remember { Animatable(0f) }
    val rightEar = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    val last = remember { IntArray(1) { -1 } }

    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        val animationsOff = Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) == 0f
        if (animationsOff) return@LaunchedEffect

        // Random, but never the same animation twice in a row
        var pick: Int
        do { pick = Random.nextInt(IconAnimation.entries.size) } while (pick == last[0])
        last[0] = pick

        // Reset any animation interrupted by a fast double tap
        rotation.snapTo(0f); offsetY.snapTo(0f); scaleX.snapTo(1f); scaleY.snapTo(1f)
        leftEar.snapTo(0f); rightEar.snapTo(0f); spin.snapTo(spin.value % 360f)

        when (IconAnimation.entries[pick]) {
            IconAnimation.Shake -> rotation.animateTo(0f, keyframes {
                durationMillis = 650
                -16f at 70; 14f at 160; -12f at 250; 10f at 340; -6f at 430; 3f at 520
            })

            IconAnimation.RingBells -> coroutineScope {
                launch {
                    rotation.animateTo(0f, keyframes {
                        durationMillis = 720
                        -5f at 60; 5f at 120; -5f at 180; 5f at 240; -5f at 300; 5f at 360; -4f at 420; 4f at 480; -2f at 560
                    })
                }
                repeat(6) {
                    launch { leftEar.animateTo(1f, tween(60, easing = LinearEasing)) }
                    rightEar.animateTo(0f, tween(60, easing = LinearEasing))
                    launch { rightEar.animateTo(1f, tween(60, easing = LinearEasing)) }
                    leftEar.animateTo(0f, tween(60, easing = LinearEasing))
                }
                launch { leftEar.animateTo(0f, spring()) }
                rightEar.animateTo(0f, spring())
            }

            IconAnimation.Jump -> {
                // Squash, jump, land with a little squash, settle
                coroutineScope {
                    launch { scaleY.animateTo(0.82f, tween(90)) }
                    scaleX.animateTo(1.12f, tween(90))
                }
                coroutineScope {
                    launch { scaleY.animateTo(1.1f, tween(160, easing = FastOutSlowInEasing)) }
                    launch { scaleX.animateTo(0.92f, tween(160)) }
                    offsetY.animateTo(-0.45f, tween(180, easing = FastOutSlowInEasing))
                }
                offsetY.animateTo(0f, tween(170, easing = LinearEasing))
                coroutineScope {
                    launch { scaleY.animateTo(0.88f, tween(70)) }
                    scaleX.animateTo(1.08f, tween(70))
                }
                coroutineScope {
                    launch { scaleY.animateTo(1f, spring(dampingRatio = 0.35f)) }
                    scaleX.animateTo(1f, spring(dampingRatio = 0.35f))
                }
            }

            IconAnimation.SpinHands -> coroutineScope {
                launch {
                    rotation.animateTo(0f, keyframes { durationMillis = 900; 4f at 450 })
                }
                spin.animateTo(spin.value + 720f, tween(900, easing = FastOutSlowInEasing))
            }
        }
    }

    ParakeetIcon(
        modifier = modifier.graphicsLayer {
            rotationZ = rotation.value
            translationY = offsetY.value * size.height
            this.scaleX = scaleX.value
            this.scaleY = scaleY.value
            transformOrigin = TransformOrigin(0.5f, 0.9f)
        },
        leftEarLift = leftEar.value,
        rightEarLift = rightEar.value,
        handsSpin = spin.value,
    )
}
