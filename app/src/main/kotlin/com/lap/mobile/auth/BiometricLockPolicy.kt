package com.lap.mobile.auth

/**
 * Decides WHEN to require biometric unlock (T188).
 *
 * Pure function — no Android, no time, no I/O — so the contract is
 * locked by JVM unit tests and the Android shell can't accidentally
 * change semantics by changing how it observes its environment.
 *
 * v1 semantics: prompt **once per process** when enabled. The
 * orchestrator (Android shell) tracks [unlockedThisProcess] and flips
 * it true after a successful BiometricPrompt callback. Process death
 * resets the flag and re-prompts at next cold launch — which is the
 * security-critical scenario (someone else picks up the device while
 * the app isn't in the foreground task list, opens it).
 *
 * Re-prompting after long background is a future refinement; v1
 * trades it for implementation simplicity. In-session navigation
 * deliberately does NOT re-prompt (already authenticated, hostile UX
 * to re-trigger).
 *
 * Why opt-in (default disabled in [BiometricLockPreference]):
 * BiometricPrompt at cold launch is a noticeable friction point.
 * Drivers who don't share the device get no security benefit and a
 * lot of UX cost — making it default-off matches user expectation
 * ("if I want a lock, I'll turn it on").
 */
object BiometricLockPolicy {
    fun shouldRequireUnlock(enabled: Boolean, unlockedThisProcess: Boolean): Boolean {
        if (!enabled) return false
        return !unlockedThisProcess
    }
}
