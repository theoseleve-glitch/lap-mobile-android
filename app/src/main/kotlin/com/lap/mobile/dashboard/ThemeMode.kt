package com.lap.mobile.dashboard

/**
 * Resolved theme mode for the dashboard's [com.lap.mobile.ui.theme.LapTheme]
 * (T208). The kernel returns one of these values; the Compose layer
 * maps each to its corresponding Material 3 colorScheme.
 *
 * Three variants:
 *   - [Light]:        default light palette (LapBlue40 primary,
 *     near-white background) — chosen when neither the high-contrast
 *     toggle nor system dark mode is active.
 *   - [Dark]:         standard dark palette (LapBlue80 primary,
 *     near-black background) — chosen when the user's system dark
 *     mode is on and the high-contrast toggle is off.
 *   - [HighContrast]: outdoor-sunlight palette (max-chroma yellow
 *     accents on pure black background) — chosen when the user
 *     has explicitly toggled high-contrast on, regardless of the
 *     system dark-mode setting.
 *
 * Why an enum, not a Boolean: this is a 3-way decision (the
 * toggle ON/OFF combined with system dark ON/OFF produces 4
 * input cells but only 3 distinct output modes — Light, Dark,
 * HighContrast). The enum makes the truth table explicit.
 */
enum class ThemeMode {
    Light,
    Dark,
    HighContrast,
}
