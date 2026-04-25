package com.lap.mobile.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks T188 contract — biometric lock as an opt-in Settings →
 * Security toggle that gates app entry behind a BiometricPrompt
 * once per process when enabled.
 *
 * The kernel splits into two pieces, each independently testable:
 *   - [BiometricLockPolicy] (this file) decides WHEN to require an
 *     unlock given the user's preference and the per-process unlock
 *     state. A pure function — no Android, no time, no I/O.
 *   - [BiometricLockPreference] holds the user's enabled/disabled
 *     toggle. Production backs it with regular SharedPreferences (NOT
 *     EncryptedSharedPreferences — this is a UI preference, not a
 *     credential). Tests use an in-memory fake.
 *
 * v1 semantics: prompt once per process when enabled. The orchestrator
 * (Android shell) tracks `unlockedThisProcess` and flips it true after
 * a successful BiometricPrompt callback. In-session navigation does
 * NOT re-prompt — that would be hostile UX. Re-prompting after long
 * background is a future refinement (T-TBD); v1 trades it for
 * implementation simplicity since the process death case (which DOES
 * re-prompt) catches the security-critical scenario.
 *
 * Why opt-in (default disabled): biometric prompt at cold launch is a
 * noticeable friction point. Drivers who don't share the device get
 * no security benefit and a lot of UX cost — making it default-off
 * matches user expectation ("if I want a lock, I'll turn it on").
 *
 * Why prefer to gate at app entry rather than per-screen: the local
 * Room DB holds session journal, captured GPS, raw sensor traces.
 * Anyone with physical device access can read those via adb if the
 * device is rooted — biometric lock at the *app* layer is a
 * social-engineering deterrent, not a hardware-key-store guarantee.
 * Per-screen gating doesn't strengthen that.
 *
 * Live device verification (actual BiometricPrompt visible, fingerprint
 * unlocks the app, fallback to PIN) is [PENDING: device-required] —
 * the policy + preference scaffold lands now so T190's auth-flow
 * coverage can wire the prompt invocation against the kernel.
 */
class BiometricLockPolicyTest {
    @Test
    fun disabled_never_requires_unlock_so_user_with_lock_off_isnt_blocked_at_cold_launch() {
        assertFalse(
            BiometricLockPolicy.shouldRequireUnlock(
                enabled = false,
                unlockedThisProcess = false,
            ),
        )
    }

    @Test
    fun disabled_doesnt_require_unlock_even_if_unlocked_state_is_stale_so_toggle_off_immediately_disables_gate() {
        // After the user flips the toggle off mid-session, any
        // pre-existing "haven't unlocked yet this process" state must
        // not trap them at the next launch / process restart.
        assertFalse(
            BiometricLockPolicy.shouldRequireUnlock(
                enabled = false,
                unlockedThisProcess = true,
            ),
        )
    }

    @Test
    fun enabled_and_not_yet_unlocked_requires_unlock_so_cold_launch_prompts_for_biometric() {
        // Headline T188 invariant: with the toggle on and a fresh
        // process, the user must complete BiometricPrompt before
        // the main UI is reached.
        assertTrue(
            BiometricLockPolicy.shouldRequireUnlock(
                enabled = true,
                unlockedThisProcess = false,
            ),
        )
    }

    @Test
    fun enabled_and_already_unlocked_doesnt_require_unlock_so_in_session_navigation_isnt_re_prompted() {
        // After the user successfully completes the prompt once per
        // process, navigating within the app must NOT re-trigger it.
        // Re-prompting on every screen change would be hostile UX
        // and there's no security benefit (already authenticated).
        assertFalse(
            BiometricLockPolicy.shouldRequireUnlock(
                enabled = true,
                unlockedThisProcess = true,
            ),
        )
    }
}
