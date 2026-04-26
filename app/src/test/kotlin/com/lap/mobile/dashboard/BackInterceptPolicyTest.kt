package com.lap.mobile.dashboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the contract for [BackInterceptPolicy] (T204) — the pure
 * function that decides "should the system back gesture be
 * intercepted right now, or should it pass through to the normal
 * navigation behavior?".
 *
 * Why a pure-function policy + a Compose handler (not a single
 * composable that hard-codes "if recording, intercept"): the
 * interception decision is the contract that drivers depend on.
 * If a future change adds a "don't warn for sub-30-second sessions"
 * carve-out, that logic belongs in the kernel where it can be
 * JVM-tested and impossible to silently break by changing how the
 * Compose layer wires up [BackInterceptPolicy.shouldIntercept].
 *
 * Two-state truth table (mirrors [RecordingStripState] exhaustively):
 *   - [RecordingStripState.Idle]      → shouldIntercept = false
 *     (let back pass through to normal dashboard-dismiss behavior)
 *   - [RecordingStripState.Recording] → shouldIntercept = true
 *     (block back gesture, show confirm dialog warning that
 *     recording continues in background)
 *
 * Why intercept is invariant to elapsed time: a 1-second-old
 * recording is still a recording. The driver's mental model is
 * binary ("am I currently capturing?"), and silently degrading
 * the safety guard for "trivially short" sessions would surprise
 * the driver who thought they were protected. The 30s-or-empty-
 * session optimization is a future-v2 question, not v1 truth.
 *
 * Why the recording state's elapsed-time payload doesn't change
 * the answer: the driver hasn't communicated "I want to discard
 * this session" — they've made a navigation gesture. The session
 * keeps recording in the background regardless. The dialog warns
 * about navigation, not about data loss.
 *
 * Pure function — no Android, no time, no I/O — so the
 * interception contract is locked here and the Compose layer
 * (BackHandler enabled flag) just mirrors the verdict.
 */
class BackInterceptPolicyTest {
    @Test
    fun idle_state_does_not_intercept_so_back_gesture_passes_through_to_normal_dashboard_dismiss() {
        // No active recording → back gesture should behave normally
        // (close the dashboard / pop nav back-stack). Intercepting
        // here would force the driver through a confirm dialog for
        // no reason and erode trust in the dialog's signal value.
        assertFalse(BackInterceptPolicy.shouldIntercept(state = RecordingStripState.Idle))
    }

    @Test
    fun recording_state_intercepts_so_back_gesture_pops_confirm_dialog_instead_of_dismissing_dashboard() {
        // Headline T204 invariant: any recording state, regardless
        // of elapsed-time payload, must intercept the back gesture.
        // This is the safety contract drivers rely on — accidental
        // edge-swipe mid-corner cannot silently dismiss the
        // dashboard while a session is active.
        val state = RecordingStripState.Recording(sessionElapsedMs = 5_000L)

        assertTrue(BackInterceptPolicy.shouldIntercept(state = state))
    }

    @Test
    fun recording_state_with_zero_elapsed_still_intercepts_so_just_started_session_is_equally_protected() {
        // A session that just started (elapsedMs = 0) is still a
        // session. The protection should not be conditional on
        // "the recording has been going long enough to matter" —
        // that would surprise a driver who tapped record then
        // immediately bumped the back edge.
        val state = RecordingStripState.Recording(sessionElapsedMs = 0L)

        assertTrue(BackInterceptPolicy.shouldIntercept(state = state))
    }

    @Test
    fun recording_state_with_large_elapsed_still_intercepts_so_endurance_session_protection_doesnt_decay() {
        // Hours-long endurance session → still intercept. No
        // "you've been recording so long it must be deliberate"
        // carve-out — the dialog is fast (one tap to dismiss)
        // and the cost of a missed warning is high (driver loses
        // visibility of the active session).
        val state = RecordingStripState.Recording(sessionElapsedMs = 3_600_000L * 2L)

        assertTrue(BackInterceptPolicy.shouldIntercept(state = state))
    }
}
