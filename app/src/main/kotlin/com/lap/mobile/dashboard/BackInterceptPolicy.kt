package com.lap.mobile.dashboard

/**
 * Pure-function policy for the dashboard's
 * [com.lap.mobile.ui.dashboard.RecordingBackInterceptHandler]
 * (T204) — decides whether the system back gesture should be
 * intercepted (warn-and-confirm) or pass through to the normal
 * navigation behavior.
 *
 * Two-state truth table, mirrors [RecordingStripState] exhaustively:
 *   - [RecordingStripState.Idle]      → false (pass through —
 *     no recording is active, dismissing the dashboard is the
 *     natural next action).
 *   - [RecordingStripState.Recording] → true (intercept — block
 *     the gesture, show a confirm dialog warning that recording
 *     continues in the background).
 *
 * Why intercept is invariant to elapsed time: drivers' mental
 * model is binary ("am I currently capturing?"). A session is a
 * session whether it's 1 second or 2 hours old, and degrading
 * the safety guard for "trivially short" sessions would surprise
 * the driver who tapped record then immediately bumped the back
 * edge. The 30s-or-empty-session optimization is a future-v2
 * question, not v1 truth.
 *
 * Why this is its own kernel rather than inline in the Compose
 * handler: future logic could grow (e.g., "skip warning if
 * recording but no samples yet", "skip warning if user has opted
 * out of confirms"). Keeping the decision in a pure object means
 * the rule is JVM-tested and impossible to silently break by
 * changing how the Compose layer wires up [shouldIntercept].
 *
 * Pure function — no Android, no time, no I/O — so the
 * Compose layer's BackHandler `enabled` flag just mirrors this
 * verdict, and the contract drivers depend on can't drift when
 * the Android back-gesture API evolves (predictive back, etc.).
 */
object BackInterceptPolicy {
    /**
     * True iff the back gesture should be intercepted in the given
     * [state]. False iff it should pass through to normal nav.
     */
    fun shouldIntercept(state: RecordingStripState): Boolean = when (state) {
        RecordingStripState.Idle -> false
        is RecordingStripState.Recording -> true
    }
}
