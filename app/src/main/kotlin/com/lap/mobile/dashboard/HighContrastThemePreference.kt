package com.lap.mobile.dashboard

/**
 * User's high-contrast theme toggle (T208).
 *
 * Two-method contract — read the current state, write the user's
 * choice. Storage-agnostic so the kernel ([ThemeChoicePolicy])
 * stays Android-free and tests can swap an in-memory fake.
 *
 * Production binds [SharedPreferencesHighContrastThemePreference]
 * which uses regular SharedPreferences (NOT EncryptedSharedPreferences)
 * — this is a UI/UX preference, not a credential. The toggle's
 * value leaking is not a meaningful disclosure.
 *
 * Default is **disabled** ([DEFAULT_ENABLED]). The high-contrast
 * theme uses pure black backgrounds and max-chroma yellow accents —
 * optimized for outdoor sunlight legibility, NOT for indoor or
 * low-light comfort. Defaulting on would force every user into a
 * sunlight-optimized palette regardless of their actual environment,
 * which would look harsh and aggressive on a desk in a normal-lit
 * room. Default-off matches user expectation: "if I'm outside, I'll
 * turn it on." Same opt-in discipline as T188 BiometricLockPreference
 * and T207 VoiceAnnouncementPreference.
 */
interface HighContrastThemePreference {
    fun isEnabled(): Boolean

    fun setEnabled(value: Boolean)

    companion object {
        const val DEFAULT_ENABLED: Boolean = false
    }
}
