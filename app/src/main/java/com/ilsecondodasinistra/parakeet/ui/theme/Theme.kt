package com.ilsecondodasinistra.parakeet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.ilsecondodasinistra.parakeet.R

// Brand palette (from the original icon)
val ParakeetDark = Color(0xFF324511)
val ParakeetDarker = Color(0xFF1E290A)
val ParakeetLime = Color(0xFFA9C607)
val ParakeetLimeTint = Color(0xFFE6EEB0)

val LcdFont = FontFamily(Font(R.font.digital_7))

val ParakeetShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val ParakeetTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontSize = 17.sp),
        bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
    )
}

/** Small, widely tracked caps for labels like "SVEGLIA". Callers upper-case the text. */
val OverlineStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.em)

private val LightColors = lightColorScheme(
    primary = ParakeetDark,
    onPrimary = Color.White,
    primaryContainer = ParakeetLimeTint,
    onPrimaryContainer = ParakeetDarker,
    secondary = Color(0xFF5B6B1C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFF3CC),
    onSecondaryContainer = ParakeetDarker,
    tertiary = ParakeetLime,
    onTertiary = ParakeetDarker,
    background = Color(0xFFFBFCF3),
    onBackground = Color(0xFF1B1D14),
    surface = Color(0xFFFBFCF3),
    onSurface = Color(0xFF1B1D14),
    surfaceVariant = Color(0xFFE4E8D0),
    onSurfaceVariant = Color(0xFF45493A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F7EA),
    surfaceContainer = Color(0xFFEFF2E2),
    surfaceContainerHigh = Color(0xFFE9EDDA),
    surfaceContainerHighest = Color(0xFFE3E8D2),
    surfaceTint = ParakeetDark,
    outline = Color(0xFF75796A),
    outlineVariant = Color(0xFFC5C9B5),
    inverseSurface = Color(0xFF2F3226),
    inverseOnSurface = Color(0xFFF1F4E4),
    inversePrimary = ParakeetLime,
)

private val DarkColors = darkColorScheme(
    primary = ParakeetLime,
    onPrimary = ParakeetDarker,
    primaryContainer = Color(0xFF3A5014),
    onPrimaryContainer = Color(0xFFEFF5C9),
    secondary = Color(0xFFC3CF7A),
    onSecondary = ParakeetDarker,
    secondaryContainer = Color(0xFF2E3A16),
    onSecondaryContainer = ParakeetLimeTint,
    tertiary = ParakeetLime,
    onTertiary = ParakeetDarker,
    background = Color(0xFF12160B),
    onBackground = Color(0xFFE3E8D2),
    surface = Color(0xFF12160B),
    onSurface = Color(0xFFE3E8D2),
    surfaceVariant = Color(0xFF2A3118),
    onSurfaceVariant = Color(0xFFC5C9B5),
    surfaceContainerLowest = Color(0xFF0D1007),
    surfaceContainerLow = Color(0xFF1A1F10),
    surfaceContainer = Color(0xFF1E2414),
    surfaceContainerHigh = Color(0xFF282F1C),
    surfaceContainerHighest = Color(0xFF333A26),
    surfaceTint = ParakeetLime,
    outline = Color(0xFF8F937F),
    outlineVariant = Color(0xFF45493A),
    inverseSurface = Color(0xFFE3E8D2),
    inverseOnSurface = Color(0xFF2F3226),
    inversePrimary = ParakeetDark,
)

@Composable
fun ParakeetTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = ParakeetShapes,
        typography = ParakeetTypography,
        content = content,
    )
}
