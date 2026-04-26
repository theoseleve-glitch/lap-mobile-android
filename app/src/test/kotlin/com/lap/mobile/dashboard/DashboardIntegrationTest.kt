package com.lap.mobile.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cross-component integration test for the dashboard composition
 * pass (T210) — closes Block J by validating that the full
 * recording-lifecycle flow produces consistent verdicts across
 * every kernel policy and ViewModel-side state.
 *
 * Why this test matters: T202 (RecordingStripState), T204
 * (BackInterceptPolicy), T205 (ScreenWakePolicy), and T206
 * (OrientationLockPolicy) all consume the same
 * [RecordingStripState] and produce independent verdicts. The
 * silent risk is **drift** — a future refactor of one policy
 * (e.g., adding a "ignore back gesture for first 5s of recording"
 * carve-out) that desynchronizes it from the others, leaving the
 * dashboard in an incoherent half-locked / half-rotatable state.
 *
 * This test exercises a realistic user flow:
 *   1. Cold launch — DashboardViewModel emits [RecordingStripState.Idle]
 *   2. User starts recording — VM emits Recording(elapsedMs)
 *   3. View dashboard — confirm all policies agree on Recording mode
 *   4. User stops recording — VM emits Idle
 *   5. Confirm all policies back to Idle defaults
 *
 * The "view dashboard" step is implicit — the VM's StateFlow IS
 * the dashboard's view contract. If state transitions correctly
 * and policies agree, the Compose layer (which is a thin
 * dispatcher over the verdicts) will too.
 *
 * Why JVM and not Compose UI test: the cross-cutting risk is
 * kernel coordination, not visual rendering. Compose UI testing
 * would add Robolectric/Espresso coupling without testing
 * additional behavior — and the dashboard widgets' own visual
 * contracts are already locked by their @Preview fixtures and
 * per-task JVM tests.
 *
 * Why no Android-device-only flow: the actual back-gesture
 * interception, screen-keep-on flag, and orientation lock all
 * require an Activity host to verify on hardware — that's
 * [PENDING: device-required] and lives in instrumented tests
 * future tasks may add. This test locks the JVM-verifiable
 * contract: "the right policies fire for the right state."
 */
class DashboardIntegrationTest {
    @Test
    fun start_recording_view_dashboard_stop_drives_consistent_verdicts_across_all_dashboard_policies() {
        val viewModel = DashboardViewModel()

        // 1. Cold launch — Idle.
        assertEquals(RecordingStripState.Idle, viewModel.state.value.recordingStripState)
        assertAllPoliciesAgreeForIdle(viewModel.state.value.recordingStripState)

        // 2. User starts recording — VM transitions to Recording(0).
        viewModel.onRecordingStarted(sessionElapsedMs = 0L)

        val activeState = viewModel.state.value.recordingStripState
        assertTrue(
            "Expected Recording state after onRecordingStarted, got $activeState",
            activeState is RecordingStripState.Recording,
        )

        // 3. View dashboard — every policy must agree on Recording mode.
        assertAllPoliciesAgreeForRecording(activeState)

        // 4. User stops recording — VM transitions back to Idle.
        viewModel.onRecordingStopped()

        assertEquals(RecordingStripState.Idle, viewModel.state.value.recordingStripState)

        // 5. Every policy must return to Idle defaults — no stale
        //    Recording-mode verdict can leak across the stop.
        assertAllPoliciesAgreeForIdle(viewModel.state.value.recordingStripState)
    }

    @Test
    fun recording_started_with_elapsed_time_propagates_to_session_elapsed_formatter_so_strip_can_render_timer() {
        // The strip (T202 RecordingStateStrip) calls
        // SessionElapsedFormatter.format(elapsedMs) to render the
        // session timer. Verify the elapsed value flows through
        // unchanged from the ViewModel API into the kernel.
        val viewModel = DashboardViewModel()

        // 90 seconds = "1:30" in the strip's M:SS format.
        viewModel.onRecordingStarted(sessionElapsedMs = 90_000L)

        val state = viewModel.state.value.recordingStripState
        assertTrue(state is RecordingStripState.Recording)
        val recording = state as RecordingStripState.Recording
        assertEquals(90_000L, recording.sessionElapsedMs)
        assertEquals("1:30", SessionElapsedFormatter.format(elapsedMs = recording.sessionElapsedMs))
    }

    @Test
    fun stop_after_long_recording_session_resets_to_idle_so_orientation_lock_releases_for_endurance_runs() {
        // Headline endurance scenario — driver recorded for 2 hours
        // then tapped Stop. The orientation lock (T206) must release
        // back to Unspecified so the user can rotate the device
        // freely on the post-session review screens. Without this
        // release, a stale SensorLandscape would persist.
        val viewModel = DashboardViewModel()

        viewModel.onRecordingStarted(sessionElapsedMs = 3_600_000L * 2L)
        assertEquals(
            DashboardOrientation.SensorLandscape,
            OrientationLockPolicy.desiredOrientation(viewModel.state.value.recordingStripState),
        )

        viewModel.onRecordingStopped()

        assertEquals(
            DashboardOrientation.Unspecified,
            OrientationLockPolicy.desiredOrientation(viewModel.state.value.recordingStripState),
        )
    }

    private fun assertAllPoliciesAgreeForIdle(state: RecordingStripState) {
        // BackIntercept (T204): no session active → don't intercept
        // back gesture (let user dismiss dashboard normally).
        assertEquals(false, BackInterceptPolicy.shouldIntercept(state = state))
        // ScreenWake (T205): no session active → respect system
        // display timeout (no battery drain when idle).
        assertEquals(false, ScreenWakePolicy.shouldKeepScreenOn(state = state))
        // Orientation (T206): no session active → unlock orientation
        // (user can rotate device freely between sessions).
        assertEquals(
            DashboardOrientation.Unspecified,
            OrientationLockPolicy.desiredOrientation(state = state),
        )
    }

    private fun assertAllPoliciesAgreeForRecording(state: RecordingStripState) {
        // BackIntercept (T204): session active → intercept back so
        // accidental edge-swipe doesn't dismiss the dashboard.
        assertEquals(true, BackInterceptPolicy.shouldIntercept(state = state))
        // ScreenWake (T205): session active → keep screen on so
        // dashboard remains glance-readable for full session.
        assertEquals(true, ScreenWakePolicy.shouldKeepScreenOn(state = state))
        // Orientation (T206): session active → lock to landscape so
        // widget layout can't re-flow mid-corner.
        assertEquals(
            DashboardOrientation.SensorLandscape,
            OrientationLockPolicy.desiredOrientation(state = state),
        )
    }
}
