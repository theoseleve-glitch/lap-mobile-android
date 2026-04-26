package com.lap.mobile.dashboard

/**
 * Pure-function policy for the dashboard's
 * [com.lap.mobile.ui.theme.LapTheme] (T208) — decides which
 * [ThemeMode] applies given the user's high-contrast toggle and
 * the system's current dark-mode setting.
 *
 * Truth table:
 *   - highContrast=true,  systemDark=any   → [ThemeMode.HighContrast]
 *   - highContrast=false, systemDark=true  → [ThemeMode.Dark]
 *   - highContrast=false, systemDark=false → [ThemeMode.Light]
 *
 * Why high-contrast wins over system dark mode: the toggle is an
 * explicit user opt-in for a specific environment (outdoor direct
 * sunlight). System dark mode is a general "I prefer darker UIs"
 * signal that doesn't say anything about ambient light. When the
 * user has explicitly said "I'm outside, give me sunlight-legible
 * UI," the system dark-mode setting is no longer the authority —
 * the toggle is. Specific overrides general.
 *
 * Why a kernel rather than inline `if (highContrast) ... else if (dark) ...`
 * in the LapTheme composable: future conditions could grow
 * (ambient-light-sensor auto-switch, force-light-for-screen-recording,
 * etc.). Pulling the verdict into a pure object means each future
 * condition is JVM-tested before merge and cannot drift when the
 * Compose theme API evolves.
 *
 * Pure function — no Android, no time, no I/O — so the verdict
 * contract is locked here and the Compose layer just dispatches
 * to its colorScheme map.
 */
object ThemeChoicePolicy {
    /**
     * Resolve the active [ThemeMode] from the inputs.
     *
     * @param highContrastEnabled the user's high-contrast toggle
     *   value (typically read from
     *   [HighContrastThemePreference.isEnabled]).
     * @param systemDarkMode the system's current dark-mode flag
     *   (typically [androidx.compose.foundation.isSystemInDarkTheme]).
     */
    fun choose(highContrastEnabled: Boolean, systemDarkMode: Boolean): ThemeMode = when {
        highContrastEnabled -> ThemeMode.HighContrast
        systemDarkMode -> ThemeMode.Dark
        else -> ThemeMode.Light
    }
}
