package com.lap.mobile.auth

/**
 * User's biometric-lock toggle (T188).
 *
 * Two-method contract — read the current state, write the user's
 * choice. Storage-agnostic so [BiometricLockPolicy] (the kernel)
 * stays Android-free and tests can swap an in-memory fake.
 *
 * Production binds [SharedPreferencesBiometricLockPreference] which
 * uses **regular** SharedPreferences (NOT [EncryptedSharedPreferences])
 * — this is a UI preference, not a credential. The toggle's value
 * leaking to a determined attacker tells them "this user might use
 * biometric lock"; that's not a meaningful disclosure. The credential
 * (the user's biometric template itself) lives in the OS-level
 * Keystore via BiometricPrompt — never in our storage.
 *
 * Default is **disabled** ([DEFAULT_ENABLED]). Biometric prompt at
 * cold launch is a noticeable friction point — drivers who don't
 * share the device get no security benefit and a lot of UX cost.
 * Default-off matches user expectation: "if I want a lock, I'll turn
 * it on."
 */
interface BiometricLockPreference {
    fun isEnabled(): Boolean

    fun setEnabled(value: Boolean)

    companion object {
        const val DEFAULT_ENABLED: Boolean = false
    }
}
