package com.lap.mobile.dashboard

/**
 * User's voice-announcement toggle (T207).
 *
 * Two-method contract — read the current state, write the user's
 * choice. Storage-agnostic so the kernel ([LapAnnouncementFormatter]
 * and any future LapAnnouncementSpeaker) stays Android-free and
 * tests can swap an in-memory fake.
 *
 * Production binds [SharedPreferencesVoiceAnnouncementPreference]
 * which uses regular SharedPreferences (NOT EncryptedSharedPreferences)
 * — this is a UI/UX preference, not a credential. The toggle's value
 * leaking to a determined attacker tells them "this user might prefer
 * voice announcements"; that's not a meaningful disclosure.
 *
 * Default is **disabled** ([DEFAULT_ENABLED]). Voice announcements
 * interrupt music, podcasts, navigation directions, and conversations
 * happening in the background — defaulting on would surprise every
 * user who enabled the dashboard for the first time. Default-off
 * matches user expectation: "if I want spoken lap times, I'll turn
 * them on." Same opt-in discipline as T188 BiometricLockPreference —
 * intrusive features must be explicitly requested.
 */
interface VoiceAnnouncementPreference {
    fun isEnabled(): Boolean

    fun setEnabled(value: Boolean)

    companion object {
        const val DEFAULT_ENABLED: Boolean = false
    }
}
