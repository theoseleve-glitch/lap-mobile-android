package com.lap.mobile.coaching

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_FILE_NAME: String = "coaching_audio_cue_prefs"
private const val KEY_ENABLED: String = "enabled"

/**
 * Android-backed [CoachingAudioCuePreference] using regular
 * (NOT encrypted) SharedPreferences.
 *
 * No encryption because the toggle's value leaking is not a
 * meaningful disclosure — see [CoachingAudioCuePreference] KDoc.
 * Same wiring shape as T207
 * [com.lap.mobile.dashboard.SharedPreferencesVoiceAnnouncementPreference];
 * different prefs file name so the two coaching/dashboard toggles
 * don't share storage keys (each lives in its own file for clean
 * separation — coaching grows its own preference surface as
 * Block K UI tasks land).
 *
 * Uses `apply()` not `commit()`: same UX-preference rationale as
 * T207 — a write lost to process death within milliseconds is
 * annoying (toggle visually flips back at next launch) but not
 * a safety failure.
 *
 * Verification of actual on-disk persistence is [PENDING:
 * device-required] — SharedPreferences is Android-only and JVM
 * tests use the in-memory fake.
 */
@Singleton
class SharedPreferencesCoachingAudioCuePreference
@Inject
constructor(
    @ApplicationContext context: Context,
) : CoachingAudioCuePreference {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    override fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, CoachingAudioCuePreference.DEFAULT_ENABLED)

    override fun setEnabled(value: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, value) }
    }
}
