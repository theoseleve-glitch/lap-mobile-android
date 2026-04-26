package com.lap.mobile.dashboard

/**
 * Pure-function policy for the dashboard's
 * [com.lap.mobile.ui.dashboard.RecordingOrientationLockHandler]
 * (T206) — decides which [DashboardOrientation] applies for the
 * given recording state.
 *
 * Two-state truth table mirrors [RecordingStripState] exhaustively:
 *   - [RecordingStripState.Idle]      → [DashboardOrientation.Unspecified]
 *     (no lock — let user/system rotate naturally; the dashboard
 *     is one of several screens and outside-recording rotation
 *     is a feature, not a bug).
 *   - [RecordingStripState.Recording] → [DashboardOrientation.SensorLandscape]
 *     (lock to landscape, sensor-decided direction; mid-session
 *     rotation would re-flow widgets and disorient the driver
 *     at the worst moment).
 *
 * Why the verdict is invariant to elapsed time: drivers' need for
 * a stable layout is binary, not progressive. A 5-second-old
 * session is just as in need of orientation lock as a 2-hour
 * endurance run. Conditional carve-outs would surprise the driver
 * who tapped record then immediately bumped the device.
 *
 * Why [DashboardOrientation.SensorLandscape] not plain Landscape:
 * drivers' mounts may place the phone in either landscape direction
 * (USB port left or right depending on cable routing). Plain
 * Landscape locks to one specific direction; SensorLandscape lets
 * the device sensor pick whichever landscape the phone is
 * physically held in. Restricting mount choice for no benefit is
 * a needless constraint.
 *
 * Why this is its own kernel rather than inline activity-mutation
 * in the Compose handler: future conditions may grow (accessibility
 * "lock portrait" override, system Auto-Rotate respect, etc.).
 * Keeping the decision in a pure object means each future condition
 * is JVM-tested before merge and cannot drift when Android's
 * orientation API evolves.
 *
 * Pure function — no Android, no time, no I/O — so the platform
 * layer's mapping (ActivityInfo.SCREEN_ORIENTATION_* constants vs
 * any future API surface) can change without touching the contract
 * drivers depend on.
 */
object OrientationLockPolicy {
    /**
     * The orientation lock that should apply in the given [state].
     */
    fun desiredOrientation(state: RecordingStripState): DashboardOrientation = when (state) {
        RecordingStripState.Idle -> DashboardOrientation.Unspecified
        is RecordingStripState.Recording -> DashboardOrientation.SensorLandscape
    }
}
