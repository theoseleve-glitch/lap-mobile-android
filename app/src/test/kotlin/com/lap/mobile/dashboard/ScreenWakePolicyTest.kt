package com.lap.mobile.dashboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the contract for [ScreenWakePolicy] (T205) — the pure
 * function that decides whether the dashboard should keep the
 * device screen on (override Android's display-timeout) for the
 * given recording state.
 *
 * Why this is a guard drivers depend on: Android's default screen-
 * off timeout is 30s-2min depending on the user's system setting.
 * A driver glances at the dashboard every ~10s during a session
 * but doesn't tap it (eyes on the road, hands on the wheel).
 * Without an active screen-keep-on, the display would black out
 * between glances, making the live dashboard useless. With the
 * keep-on enabled during recording the screen stays bright; when
 * recording stops the flag clears and normal timeout resumes
 * (preserving battery when idle).
 *
 * Two-state truth table mirrors [RecordingStripState] exhaustively:
 *   - [RecordingStripState.Idle]      → false (let screen timeout
 *     normally — no need to fight the system display policy when
 *     the user isn't actively recording).
 *   - [RecordingStripState.Recording] → true (keep screen on so
 *     the dashboard is glance-readable for the entire session).
 *
 * Why the verdict is invariant to elapsed time: drivers' need
 * for a glance-readable dashboard doesn't depend on session
 * length. A 5-second-old session is just as in-need of a stay-
 * awake screen as a 2-hour-old endurance run. Conditional logic
 * like "only keep on after 30 seconds" would surprise the driver
 * who tapped record then immediately checked the dashboard and
 * found it black.
 *
 * Why this is its own kernel rather than inline in the Compose
 * handler: future conditions may grow (battery-saver mode opt-out,
 * "low-power profile" user toggle, accessibility "always on"
 * override). Keeping the decision in a pure object means each
 * future condition gets JVM-tested before merge and is impossible
 * to silently break by changing how the Compose layer wires up
 * the view.keepScreenOn property.
 */
class ScreenWakePolicyTest {
    @Test
    fun idle_state_does_not_keep_screen_on_so_normal_display_timeout_applies_when_user_isnt_recording() {
        // No active recording → respect the user's system display
        // timeout. Forcing screen-on outside of recording would be
        // surprising, drain battery, and erode trust in the keep-on
        // signal during actual sessions.
        assertFalse(ScreenWakePolicy.shouldKeepScreenOn(state = RecordingStripState.Idle))
    }

    @Test
    fun recording_state_keeps_screen_on_so_dashboard_remains_glance_readable_for_entire_session() {
        // Headline T205 invariant: any recording state keeps the
        // screen on. This is the core driver-experience contract —
        // the dashboard must never go dark while a session is
        // active, regardless of how long since the last touch.
        val state = RecordingStripState.Recording(sessionElapsedMs = 5_000L)

        assertTrue(ScreenWakePolicy.shouldKeepScreenOn(state = state))
    }

    @Test
    fun recording_with_zero_elapsed_keeps_screen_on_so_just_started_session_isnt_briefly_dark() {
        // A session that just started (elapsedMs = 0) needs the
        // screen up immediately. Any delay ("only keep on after
        // 30s") would surprise the driver who tapped record then
        // immediately checked the dashboard and found it dark.
        val state = RecordingStripState.Recording(sessionElapsedMs = 0L)

        assertTrue(ScreenWakePolicy.shouldKeepScreenOn(state = state))
    }

    @Test
    fun recording_with_long_elapsed_keeps_screen_on_so_endurance_session_doesnt_decay_to_dark_after_hours() {
        // Multi-hour endurance session → still keep on. The screen
        // must stay glance-readable for the full duration; drivers
        // expect the dashboard to behave consistently from start
        // to stop regardless of session length.
        val state = RecordingStripState.Recording(sessionElapsedMs = 3_600_000L * 4L)

        assertTrue(ScreenWakePolicy.shouldKeepScreenOn(state = state))
    }
}
