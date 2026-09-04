package io.github.xalrk.nudge.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.xalrk.nudge.data.ThemeMode

/*
 * Flat, single-accent palette derived from one user-chosen color. Light mode is plain
 * white; dark mode is true black so AMOLED panels switch those pixels off. No gradients,
 * no tinted elevation.
 */

private fun mix(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

private fun onColorFor(bg: Color): Color = if (bg.luminance() < 0.4f) Color.White else Color(0xFF111111)

fun lightScheme(accent: Color): ColorScheme {
    val primary = accent
    return lightColorScheme(
        primary = primary,
        onPrimary = onColorFor(primary),
        primaryContainer = mix(primary, Color.White, 0.85f),
        onPrimaryContainer = mix(primary, Color.Black, 0.5f),
        secondary = primary,
        onSecondary = onColorFor(primary),
        secondaryContainer = mix(primary, Color.White, 0.85f),
        onSecondaryContainer = mix(primary, Color.Black, 0.5f),
        tertiary = primary,
        onTertiary = onColorFor(primary),
        tertiaryContainer = mix(primary, Color.White, 0.85f),
        onTertiaryContainer = mix(primary, Color.Black, 0.5f),
        surfaceTint = Color.White,
        background = Color.White,
        onBackground = Color(0xFF111111),
        surface = Color.White,
        onSurface = Color(0xFF111111),
        surfaceVariant = Color(0xFFF2F2F2),
        onSurfaceVariant = Color(0xFF616161),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFFAFAFA),
        surfaceContainer = Color(0xFFF6F6F6),
        surfaceContainerHigh = Color(0xFFEFEFEF),
        surfaceContainerHighest = Color(0xFFE8E8E8),
        outline = Color(0xFFBDBDBD),
        outlineVariant = Color(0xFFE0E0E0),
        error = Color(0xFFD32F2F),
    )
}

fun amoledScheme(accent: Color): ColorScheme {
    // Lift the accent so it keeps contrast on pure black; very dark accents get lifted more.
    val primary = mix(accent, Color.White, if (accent.luminance() < 0.05f) 0.65f else 0.3f)
    return darkColorScheme(
        primary = primary,
        onPrimary = onColorFor(primary),
        primaryContainer = mix(accent, Color.Black, 0.55f),
        onPrimaryContainer = mix(primary, Color.White, 0.6f),
        secondary = primary,
        onSecondary = onColorFor(primary),
        secondaryContainer = mix(accent, Color.Black, 0.55f),
        onSecondaryContainer = mix(primary, Color.White, 0.6f),
        tertiary = primary,
        onTertiary = onColorFor(primary),
        tertiaryContainer = mix(accent, Color.Black, 0.55f),
        onTertiaryContainer = mix(primary, Color.White, 0.6f),
        surfaceTint = Color.Black,
        background = Color.Black,
        onBackground = Color(0xFFEDEDED),
        surface = Color.Black,
        onSurface = Color(0xFFEDEDED),
        surfaceVariant = Color(0xFF161616),
        onSurfaceVariant = Color(0xFF9E9E9E),
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF070707),
        surfaceContainer = Color(0xFF0D0D0D),
        surfaceContainerHigh = Color(0xFF161616),
        surfaceContainerHighest = Color(0xFF1F1F1F),
        outline = Color(0xFF5C5C5C),
        outlineVariant = Color(0xFF262626),
        error = Color(0xFFEF5350),
    )
}

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
fun NudgeTheme(mode: ThemeMode, dynamicColor: Boolean, accentArgb: Int, content: @Composable () -> Unit) {
    val dark = isDark(mode)
    val ctx = LocalContext.current
    val scheme = remember(dark, dynamicColor, accentArgb) {
        if (dynamicColor && Build.VERSION.SDK_INT >= 31) {
            if (dark) dynamicDarkColorScheme(ctx).amoled() else dynamicLightColorScheme(ctx)
        } else {
            val accent = Color(accentArgb)
            if (dark) amoledScheme(accent) else lightScheme(accent)
        }
    }
    MaterialTheme(colorScheme = scheme, shapes = NudgeShapes, content = content)
}
