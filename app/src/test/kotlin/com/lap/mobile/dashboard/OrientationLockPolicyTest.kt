package com.lap.mobile.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the contract for [OrientationLockPolicy] (T206) — the
 * pure function that decides which orientation lock applies to
 * the dashboard given the current recording state.
 *
 * Why orientation-lock during recording matters: drivers typically
 * mount their phone on a steering wheel or dash mount in landscape,
 * and the dashboard widgets (T193 SpeedGauge, T194 LapDisplay, T195
 * TrackMap, T196 SpeedOverDistance, T197 GgDiagram, T198-T201
 * status pills, T202 RecordingStateStrip) are sized and arranged
 * for landscape. An accidental rotation mid-corner — caused by a
 * bumpy track surface, a hand brushing the screen, or briefly
 * unmounted phone — would re-flow the layout, hide widgets, and
 * disorient the driver at the worst possible time. Forcing
 * landscape during recording removes that whole class of failure.
 *
 * Why allow rotation outside recording: the dashboard is not the
 * app's only screen. Users browsing recent sessions, configuring
 * pairing (T180), reviewing settings, or just hand-holding the
 * device pre-session benefit from natural portrait/landscape
 * rotation. Locking orientation always would be a needless
 * constraint outside the active driving window.
 *
 * Two-state truth table, mirrors [RecordingStripState] exhaustively:
 *   - [RecordingStripState.Idle]      → [DashboardOrientation.Unspecified]
 *     (let user/system pick — natural rotation behavior).
 *   - [RecordingStripState.Recording] → [DashboardOrientation.SensorLandscape]
 *     (lock to whichever landscape the device is physically held
 *     in — accommodates either mount direction).
 *
 * Why SensorLandscape, not plain Landscape: drivers' mounts may
 * place the phone in either landscape direction (USB port left or
 * right depending on cable routing). Plain Landscape locks to one
 * specific orientation; SensorLandscape lets the device sensor
 * pick whichever landscape the phone is physically held in.
 * Restricting mount choice for no benefit is a needless constraint.
 *
 * Why this is its own kernel rather than a hard-coded
 * activity.requestedOrientation in the Compose handler: future
 * conditions may grow (e.g., "respect user's accessibility-level
 * 'lock portrait' setting", "force landscape only if Auto-Rotate
 * is enabled in system settings"). Pulling the verdict into a
 * pure object means each future condition is JVM-tested before
 * merge and cannot drift when Android's orientation API evolves.
 */
class OrientationLockPolicyTest {
    @Test
    fun idle_state_returns_unspecified_so_user_can_rotate_freely_when_not_recording() {
        // No active recording → no orientation lock. Users browsing
        // sessions list / settings / pairing screens benefit from
        // natural rotation. Locking always would be a needless
        // constraint outside the active driving window.
        assertEquals(
            DashboardOrientation.Unspecified,
            OrientationLockPolicy.desiredOrientation(state = RecordingStripState.Idle),
        )
    }

    @Test
    fun recording_state_returns_sensor_landscape_so_dashboard_layout_stays_stable_mid_session() {
        // Headline T206 invariant: any recording state locks
        // orientation to SensorLandscape. Removes the whole class
        // of failure where an accidental rotation mid-corner
        // re-flows the layout, hides widgets, and disorients
        // the driver at the worst possible time.
        val state = RecordingStripState.Recording(sessionElapsedMs = 5_000L)

        assertEquals(
            DashboardOrientation.SensorLandscape,
            OrientationLockPolicy.desiredOrientation(state = state),
        )
    }

    @Test
    fun recording_with_zero_elapsed_locks_orientation_so_just_started_session_isnt_briefly_rotatable() {
        // A session that just started (elapsedMs = 0) needs the
        // orientation lock immediately. Any delay ("only lock
        // after 30s") would surprise the driver who tapped
        // record then immediately bumped the device.
        val state = RecordingStripState.Recording(sessionElapsedMs = 0L)

        assertEquals(
            DashboardOrientation.SensorLandscape,
            OrientationLockPolicy.desiredOrientation(state = state),
        )
    }

    @Test
    fun recording_with_long_elapsed_keeps_orientation_locked_so_endurance_session_layout_doesnt_flip_after_hours() {
        // Multi-hour endurance session → still locked. The
        // orientation guard must hold for the full duration —
        // a spurious flip 2 hours into a race would be
        // catastrophic for driver concentration.
        val state = RecordingStripState.Recording(sessionElapsedMs = 3_600_000L * 4L)

        assertEquals(
            DashboardOrientation.SensorLandscape,
            OrientationLockPolicy.desiredOrientation(state = state),
        )
    }
}
