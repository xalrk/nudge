package io.github.xalrk.nudge.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.xalrk.nudge.data.ThemeMode

/*
 * Flat, single-accent palette. Light mode is plain white; dark mode is true black so
 * AMOLED panels switch those pixels off. No gradients, no tinted elevation.
 */
private val Accent = Color(0xFF3D5AFE)
private val AccentDark = Color(0xFF8C9EFF)

private val Light = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EBFF),
    onPrimaryContainer = Color(0xFF0B1B5E),
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF3F0),
    onSecondaryContainer = Color(0xFF00352F),
    tertiary = Color(0xFFF57C00),
    background = Color.White,
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF616161),
    surfaceContainer = Color(0xFFF6F6F6),
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainerHigh = Color(0xFFEFEFEF),
    surfaceContainerHighest = Color(0xFFE8E8E8),
    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFFE0E0E0),
    error = Color(0xFFD32F2F),
)

private val Amoled = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF00105C),
    primaryContainer = Color(0xFF1A237E),
    onPrimaryContainer = Color(0xFFE8EBFF),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF00352F),
    secondaryContainer = Color(0xFF004D43),
    onSecondaryContainer = Color(0xFFDDF3F0),
    tertiary = Color(0xFFFFB74D),
    background = Color.Black,
    onBackground = Color(0xFFEDEDED),
    surface = Color.Black,
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF161616),
    onSurfaceVariant = Color(0xFF9E9E9E),
    surfaceContainer = Color(0xFF0D0D0D),
    surfaceContainerLow = Color(0xFF070707),
    surfaceContainerHigh = Color(0xFF161616),
    surfaceContainerHighest = Color(0xFF1F1F1F),
    outline = Color(0xFF5C5C5C),
    outlineVariant = Color(0xFF262626),
    error = Color(0xFFEF5350),
)

/** Force true black onto a dynamic (Material You) dark scheme. */
private fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black, surface = Color.Black,
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF070707),
    surfaceContainer = Color(0xFF0D0D0D), surfaceContainerHigh = Color(0xFF161616),
    surfaceContainerHighest = Color(0xFF1F1F1F), surfaceVariant = Color(0xFF161616),
)

private val NudgeShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun NudgeTheme(mode: ThemeMode, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = isDark(mode)
    val scheme = if (dynamicColor && Build.VERSION.SDK_INT >= 31) {
        val ctx = LocalContext.current
        if (dark) dynamicDarkColorScheme(ctx).amoled() else dynamicLightColorScheme(ctx)
    } else if (dark) Amoled else Light
    MaterialTheme(colorScheme = scheme, shapes = NudgeShapes, content = content)
}
