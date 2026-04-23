package com.lap.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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

@Composable
fun LapTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = LapTypography,
        shapes = LapShapes,
        content = content,
    )
}
