package com.lap.mobile.dashboard

/**
 * Lifecycle state for the dashboard's persistent
 * [com.lap.mobile.ui.dashboard.RecordingStateStrip] (T202).
 *
 * Two variants only — drivers need an unambiguous answer to "am I
 * capturing right now?":
 *   - [Idle]:      no session active, nothing being persisted.
 *   - [Recording]: capture pipeline is running; carries the
 *                  session-elapsed time the strip renders.
 *
 * Sealed interface (not enum) because [Recording] carries a payload
 * (the elapsed milliseconds the strip displays) and [Idle] does
 * not. Matches Kotlin's idiomatic sum-type shape — same pattern as
 * [com.lap.mobile.shared.Result.Success] / Failure where only some
 * variants carry data.
 *
 * No "Paused" or "Finalizing" variants in v1 — capture either runs
 * or it doesn't. The kernel collapses other transient states (e.g.,
 * the brief "starting" window between user-tap and first sample)
 * to whichever side they functionally belong to: pre-first-sample
 * → still Idle (nothing committed), first-sample-onward →
 * Recording. If the foreground service ever needs a "winding down"
 * variant for graceful-stop UX, the sealed grows a state.
 */
sealed interface RecordingStripState {
    data object Idle : RecordingStripState

    /**
     * Active capture session — [sessionElapsedMs] is the wall-clock
     * elapsed time since the user tapped record (NOT the lap-elapsed
     * which T194 [com.lap.mobile.ui.dashboard.LapDisplay] handles).
     *
     * Long, not Duration, because the upstream timer and the strip's
     * [SessionElapsedFormatter] both work in millisecond longs —
     * passing Duration would force conversion at the boundary with
     * no semantic gain.
     */
    data class Recording(val sessionElapsedMs: Long) : RecordingStripState
}
