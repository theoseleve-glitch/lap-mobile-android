package com.lap.mobile.dashboard

/**
 * Live-dashboard UI state (T192 scaffold).
 *
 * Holds the values the dashboard renders. Defaults are the
 * "pre-session, cold-launch" view: speed gauge at 0, lap counter at
 * 0, elapsed timer at 0, not recording. Locking those defaults means
 * T193 (speed gauge), T194 (lap counter + timer), and T195 (track
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
 * Field shape locked here so T196+ (data wiring) doesn't reshape the
 * record and force the Composables to update mid-sprint. Track-map
 * GPS trail intentionally NOT included yet — T195 picks the right
 * shape for performant 10Hz redraws (likely a buffer of (lat, lon)
 * pairs, possibly with a max-points cap for memory bounds).
 */
data class DashboardUiState(
    val currentSpeedKmh: Double = 0.0,
    val currentLapNumber: Int = 0,
    val currentLapElapsedMs: Long = 0L,
    val isRecording: Boolean = false,
)
