package com.ilsecondodasinistra.parakeet.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Frosted glass over the content registered with `hazeSource`: strong blur, light tint, so what
 * scrolls underneath stays recognisable as colour and shape (HazeMaterials.thin was almost opaque).
 */
@Composable
fun Modifier.glass(state: HazeState, tintAlpha: Float = 0.55f, blur: Dp = 24.dp): Modifier {
    val surface = MaterialTheme.colorScheme.surface
    return hazeEffect(
        state = state,
        style = HazeStyle(
            backgroundColor = surface,
            tints = listOf(HazeTint(surface.copy(alpha = tintAlpha))),
            blurRadius = blur,
            noiseFactor = 0.08f,
        ),
    ).pointerInput(Unit) {} // taps on the glass must not reach what is underneath
}
