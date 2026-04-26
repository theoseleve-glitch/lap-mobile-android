package com.lap.mobile.dashboard

/**
 * Pure-function policy for the dashboard's
 * [com.lap.mobile.ui.dashboard.RecordingScreenWakeHandler]
 * (T205) — decides whether the device screen should be kept
 * awake (display-timeout overridden) for the given recording
 * state.
 *
 * Two-state truth table mirrors [RecordingStripState] exhaustively:
 *   - [RecordingStripState.Idle]      → false (let the system
 *     display timeout apply normally — no need to fight the user's
 *     timeout setting when no session is active).
 *   - [RecordingStripState.Recording] → true (override timeout so
 *     the dashboard remains glance-readable for the full session).
 *
 * Why intercept is invariant to elapsed time: drivers' need for a
 * glance-readable dashboard is binary, not progressive. A 5-second
 * session is just as in need of stay-awake as a 2-hour endurance
 * run. Conditional carve-outs ("only keep on after 30s") would
 * surprise the driver who tapped record then immediately checked
 * the dashboard and found it dark.
 *
 * Why this is its own kernel rather than inline in the handler:
 * future conditions may grow (battery-saver opt-out, low-power
 * profile toggle, accessibility "always on" override). Keeping
 * the decision in a pure object means each future condition is
 * JVM-tested before merge and cannot be silently broken by
 * changing how the Compose layer wires up the view-level
 * keep-screen-on flag.
 *
 * Pure function — no Android, no time, no I/O — so the platform
 * layer's choice (View.keepScreenOn vs Window.FLAG_KEEP_SCREEN_ON
 * vs WakeLock) can change without touching the contract drivers
 * depend on.
 */
object ScreenWakePolicy {
    /**
     * True iff the screen should be kept awake (display-timeout
     * overridden) in the given [state]. False iff the system
     * display timeout should apply normally.
     */
    fun shouldKeepScreenOn(state: RecordingStripState): Boolean = when (state) {
        RecordingStripState.Idle -> false
        is RecordingStripState.Recording -> true
    }
}
