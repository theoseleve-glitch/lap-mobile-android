package com.lap.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.lap.mobile.dashboard.ThemeChoicePolicy
import com.lap.mobile.dashboard.ThemeMode

private val LightColors = lightColorScheme(
    primary = LapBlue40,
    onPrimary = LapNeutral100,
    primaryContainer = LapBlue90,
    onPrimaryContainer = LapBlue10,
    secondary = LapAmber40,
    onSecondary = LapNeutral100,
    secondaryContainer = LapAmber90,
    onSecondaryContainer = LapAmber10,
    background = LapNeutral99,
    onBackground = LapNeutral10,
    surface = LapNeutral100,
    onSurface = LapNeutral10,
    error = LapError40,
    onError = LapNeutral100,
)

private val DarkColors = darkColorScheme(
    primary = LapBlue80,
    onPrimary = LapBlue10,
    primaryContainer = LapBlue10,
    onPrimaryContainer = LapBlue90,
    secondary = LapAmber80,
    onSecondary = LapAmber10,
    secondaryContainer = LapAmber10,
    onSecondaryContainer = LapAmber90,
    background = LapNeutral10,
    onBackground = LapNeutral90,
    surface = LapNeutral20,
    onSurface = LapNeutral90,
    error = LapError80,
    onError = LapNeutral10,
)

// High-contrast colorScheme (T208) — outdoor sunlight palette.
// Pure black background + max-chroma accents. Built on darkColorScheme
// because Material 3 expects dark-base when surfaces are pure-dark.
// All container colors saturated so status pills (T198/T200/T201)
// pop against ambient brightness; all on* colors max-contrast against
// their backgrounds for legibility under sun glare.
private val HighContrastColors = darkColorScheme(
    primary = LapHighContrastYellow,
    onPrimary = LapHighContrastBlack,
    primaryContainer = LapHighContrastYellow,
    onPrimaryContainer = LapHighContrastBlack,
    secondary = LapHighContrastCyan,
    onSecondary = LapHighContrastBlack,
    secondaryContainer = LapHighContrastCyan,
    onSecondaryContainer = LapHighContrastBlack,
    tertiary = LapHighContrastMagenta,
    onTertiary = LapHighContrastBlack,
    tertiaryContainer = LapHighContrastMagenta,
    onTertiaryContainer = LapHighContrastBlack,
    background = LapHighContrastBlack,
    onBackground = LapHighContrastWhite,
    surface = LapHighContrastBlack,
    onSurface = LapHighContrastWhite,
    surfaceVariant = LapHighContrastSurface,
    onSurfaceVariant = LapHighContrastWhite,
    error = LapHighContrastRed,
    onError = LapHighContrastWhite,
    errorContainer = LapHighContrastRed,
    onErrorContainer = LapHighContrastWhite,
    outline = LapHighContrastWhite,
)

/**
 * Root theme for the LAP app.
 *
 * The [useHighContrast] parameter (T208) overrides [darkTheme] when
 * true — it represents an explicit user opt-in to the outdoor-
 * sunlight palette. The verdict is computed by the JVM-tested
 * [ThemeChoicePolicy] so the truth table is locked across all
 * call sites.
 *
 * Backwards-compatible: existing call sites that do `LapTheme { ... }`
 * default `useHighContrast` to false, getting the same Light/Dark
 * behavior they had before T208.
 */
@Composable
fun LapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useHighContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val mode = ThemeChoicePolicy.choose(
        highContrastEnabled = useHighContrast,
        systemDarkMode = darkTheme,
    )
    val colors = when (mode) {
        ThemeMode.Light -> LightColors
        ThemeMode.Dark -> DarkColors
        ThemeMode.HighContrast -> HighContrastColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = LapTypography,
        shapes = LapShapes,
        content = content,
    )
}
