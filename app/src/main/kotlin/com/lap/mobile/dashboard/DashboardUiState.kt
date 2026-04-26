package com.lap.mobile.dashboard

/**
 * Live-dashboard UI state (T192 scaffold + T210 RecordingStripState
 * integration).
 *
 * Holds the values the dashboard renders. Defaults are the
 * "pre-session, cold-launch" view: speed gauge at 0, lap counter at
 * 0, elapsed timer at 0, recording state Idle. Locking those defaults
 * means T193 (speed gauge), T194 (lap counter + timer), and T195 (track
 * map) inherit a stable cold-launch contract — adding a field
 * elsewhere doesn't suddenly produce blank/dash placeholders for
 * existing fields.
 *
 * Why zeros not nulls: drivers expect a working dashboard the moment
 * the screen renders. Showing "—" or "—:—:—" pre-session implies
 * "we don't know yet" which is true but unhelpful — the gauge at 0
 * on a stationary car is correct AND avoids the "is this thing
 * working?" question.
 *
 * Why [recordingStripState] is a sealed [RecordingStripState] (T202)
 * not a Boolean isRecording: with the boolean, code that needs the
 * recording's elapsed time has to maintain it in a *separate* field
 * that could drift from the boolean flag. With the sealed type,
 * "I'm recording" and "I've been recording for X ms" are inseparable —
 * the state IS the data. T204 (BackInterceptPolicy), T205
 * (ScreenWakePolicy), and T206 (OrientationLockPolicy) all read this
 * field as their single source of lifecycle truth.
 *
 * Field shape locked here so T211+ (rules engine, capture wiring)
 * doesn't reshape the record and force the Composables to update
 * mid-sprint. Track-map GPS trail intentionally NOT included yet —
 * T195 picks the right shape for performant 10Hz redraws (likely a
 * buffer of (lat, lon) pairs, possibly with a max-points cap for
 * memory bounds).
 */
data class DashboardUiState(
    val currentSpeedKmh: Double = 0.0,
    val currentLapNumber: Int = 0,
    val currentLapElapsedMs: Long = 0L,
    val recordingStripState: RecordingStripState = RecordingStripState.Idle,
)
