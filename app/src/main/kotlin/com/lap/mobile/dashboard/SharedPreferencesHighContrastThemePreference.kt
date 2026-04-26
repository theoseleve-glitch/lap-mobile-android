package com.lap.mobile.dashboard

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_FILE_NAME: String = "high_contrast_theme_prefs"
private const val KEY_ENABLED: String = "enabled"

/**
 * Android-backed [HighContrastThemePreference] using regular
 * (NOT encrypted) SharedPreferences.
 *
 * No encryption because the toggle's value leaking is not a
 * meaningful disclosure — see [HighContrastThemePreference] KDoc.
 * Same wiring shape as T188's
 * [com.lap.mobile.auth.SharedPreferencesBiometricLockPreference]
 * and T207's
 * [SharedPreferencesVoiceAnnouncementPreference]; different prefs
 * file name so the three toggles don't share storage keys (each
 * lives in its own file for clean separation).
 *
 * Uses `apply()` not `commit()`: the toggle is a UX preference. If
 * a write is lost to a process death within milliseconds of the user
 * tapping the toggle, the worst case is the toggle visually flips
 * back at next launch — annoying, not a safety failure.
 *
 * Verification of actual on-disk persistence is [PENDING:
 * device-required] — SharedPreferences is Android-only.
 */
@Singleton
class SharedPreferencesHighContrastThemePreference
@Inject
constructor(
    @ApplicationContext context: Context,
) : HighContrastThemePreference {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    override fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, HighContrastThemePreference.DEFAULT_ENABLED)

    override fun setEnabled(value: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, value) }
    }
}
