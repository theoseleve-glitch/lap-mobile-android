package com.lap.mobile.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_FILE_NAME: String = "biometric_lock_prefs"
private const val KEY_ENABLED: String = "enabled"

/**
 * Android-backed [BiometricLockPreference] using regular
 * (NOT encrypted) SharedPreferences.
 *
 * No encryption because the toggle's value leaking is not a
 * meaningful disclosure — see [BiometricLockPreference] KDoc.
 *
 * Uses `apply()` not `commit()`: the toggle is a UX preference. If a
 * write is lost to a process death within milliseconds of the user
 * tapping the toggle, the worst case is the toggle visually flips
 * back at next launch — annoying, not a security failure. T173's
 * EncryptedPreferencesTokenStorage uses `commit()` because losing a
 * freshly-rotated refresh token would log the user out.
 *
 * Verification of actual on-disk persistence is [PENDING:
 * device-required] — SharedPreferences is Android-only.
 */
@Singleton
class SharedPreferencesBiometricLockPreference
@Inject
constructor(
    @ApplicationContext context: Context,
) : BiometricLockPreference {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    override fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, BiometricLockPreference.DEFAULT_ENABLED)

    override fun setEnabled(value: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, value) }
    }
}
