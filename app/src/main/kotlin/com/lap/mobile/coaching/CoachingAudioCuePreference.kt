package com.lap.mobile.coaching

/**
 * User's coaching-audio-cue toggle (T218).
 *
 * Two-method contract — read the current state, write the user's
 * choice. Storage-agnostic so the kernel ([CoachingAudioCueFormatter])
 * stays Android-free and tests can swap an in-memory fake. Mirrors
 * T207 [com.lap.mobile.dashboard.VoiceAnnouncementPreference]'s
 * shape exactly so a future Settings host screen can render both
 * toggles uniformly.
 *
 * Production binds [SharedPreferencesCoachingAudioCuePreference]
 * via [com.lap.mobile.di.DashboardModule]. Regular SharedPreferences
 * (NOT EncryptedSharedPreferences) — this is a UX preference, not
 * a credential. Same security analysis as T207's voice toggle.
 *
 * Default is **disabled** ([DEFAULT_ENABLED]). Audio cues during
 * recording interrupt music, podcasts, navigation, and conversations
 * — defaulting on would surprise every user. Default-off matches
 * the user expectation: "if I want spoken coaching tips, I'll turn
 * them on." Same opt-in discipline as T207 voice announcements
 * and T188 biometric lock.
 */
interface CoachingAudioCuePreference {
    fun isEnabled(): Boolean

    fun setEnabled(value: Boolean)

    companion object {
        const val DEFAULT_ENABLED: Boolean = false
    }
}
