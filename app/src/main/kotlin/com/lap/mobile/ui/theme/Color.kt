package com.lap.mobile.ui.theme

import androidx.compose.ui.graphics.Color

// Brand palette — racing/telemetry feel. Deep blue primary, amber secondary.
// Token names follow Material 3 convention: <role><tone>. Tone ~40 for light key
// colors, ~80 for dark key colors.

internal val LapBlue40 = Color(0xFF1565C0)
internal val LapBlue80 = Color(0xFF9FC0F8)
internal val LapBlue10 = Color(0xFF001A41)
internal val LapBlue90 = Color(0xFFD9E2FF)

internal val LapAmber40 = Color(0xFFE65100)
internal val LapAmber80 = Color(0xFFFFCC80)
internal val LapAmber10 = Color(0xFF4A2500)
internal val LapAmber90 = Color(0xFFFFDFC1)

internal val LapNeutral10 = Color(0xFF1A1A1A)
internal val LapNeutral20 = Color(0xFF2E2E2E)
internal val LapNeutral90 = Color(0xFFEAEAEA)
internal val LapNeutral99 = Color(0xFFFAFAFA)
internal val LapNeutral100 = Color(0xFFFFFFFF)

internal val LapError40 = Color(0xFFB3261E)
internal val LapError80 = Color(0xFFF2B8B5)

// High-contrast palette (T208) — outdoor sunlight legibility.
// Pure black background absorbs ambient light; max-chroma accents
// stay visible when the screen reflects direct sun. NOT a comfort
// palette — explicitly optimized for "I cannot read my screen
// because the sun is on it." Matches NHRA / F1 dashboard convention:
// pure black + saturated yellow primary + red for warnings.
internal val LapHighContrastBlack = Color(0xFF000000)
internal val LapHighContrastSurface = Color(0xFF1A1A1A)
internal val LapHighContrastWhite = Color(0xFFFFFFFF)
internal val LapHighContrastYellow = Color(0xFFFFD600)
internal val LapHighContrastCyan = Color(0xFF00E5FF)
internal val LapHighContrastMagenta = Color(0xFFFF40FF)
internal val LapHighContrastRed = Color(0xFFFF1744)
