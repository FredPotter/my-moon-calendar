package com.fredypot.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

private val LightColors = lightColorScheme(
    primary = Color(0xFFB3566F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFBDDE4),
    onPrimaryContainer = Color(0xFF4A1F2B),
    secondary = Color(0xFF8E6E79),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5E4E9),
    onSecondaryContainer = Color(0xFF3D2730),
    tertiary = Color(0xFF7D68A8),
    tertiaryContainer = Color(0xFFECE3F7),
    onTertiaryContainer = Color(0xFF2F2350),
    background = Color(0xFFFFF8F9),
    onBackground = Color(0xFF3D2E33),
    surface = Color(0xFFFFF8F9),
    onSurface = Color(0xFF3D2E33),
    onSurfaceVariant = Color(0xFF7A6268),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFDF2F4),
    surfaceContainer = Color(0xFFFBEDF0),
    surfaceContainerHigh = Color(0xFFF8E7EB),
    surfaceContainerHighest = Color(0xFFF4E0E5),
    outline = Color(0xFFD3BBC2),
    outlineVariant = Color(0xFFEEDDE2),
)

/** Colors of the cycle phases on the calendar. */
@Immutable
data class CycleColors(
    val period: Color,
    val onPeriod: Color,
    val predictedPeriod: Color,
    val predictedPeriodBorder: Color,
    val fertile: Color,
    val ovulation: Color,
    val onOvulation: Color,
)

private val LightCycleColors = CycleColors(
    period = Color(0xFFF4A3B8),
    onPeriod = Color(0xFF4A1F2B),
    predictedPeriod = Color(0xFFFDEBF0),
    predictedPeriodBorder = Color(0xFFEE8FA8),
    fertile = Color(0xFFEFE6FA),
    ovulation = Color(0xFFCDB8EE),
    onOvulation = Color(0xFF2F2350),
)

val LocalCycleColors = staticCompositionLocalOf { LightCycleColors }

private val BaseTypography = Typography()

private val AppTypography = BaseTypography.copy(
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.Light),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.Normal),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.Normal),
)

/** Always the light pink theme, regardless of the system dark mode. */
@Composable
fun MoonTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCycleColors provides LightCycleColors) {
        MaterialTheme(
            colorScheme = LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
