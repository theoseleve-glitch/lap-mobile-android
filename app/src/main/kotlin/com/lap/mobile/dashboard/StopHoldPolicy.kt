package com.lap.mobile.dashboard

/**
 * Pure-function policy for the dashboard's
 * [com.lap.mobile.ui.dashboard.StopButton] (T203) hold-to-stop
 * gesture. Decides "is the current hold long enough to count as a
 * deliberate stop intent?" and "what fraction of the way to
 * activation is the current hold?".
 *
 * Two-layer safety design (matching every "destructive action"
 * pattern in mature mobile UX):
 *   1. Hold ≥ [HOLD_THRESHOLD_MS] — filters accidental contact
 *      (brushed finger, finger resting on screen mid-corner).
 *   2. Confirm dialog after release — filters intentional-but-
 *      mistaken hold (driver meant to tap a different button).
 *
 * Threshold = 500ms — the established Material 3 long-press and
 * iOS context-menu activation baseline. Below 300ms is too easy to
 * trigger by accident; above 1s is punitive when the driver
 * actually wants to stop. Drivers' muscle memory already maps
 * "0.5s hold" to "deliberate gesture."
 *
 * Threshold is INCLUSIVE — exactly 500ms activates. Boundary
 * belongs to the "hold succeeded" band so a precisely-timed hold
 * isn't rejected by 1ms of clock rounding.
 *
 * Defensive: negative durations (clock skew, race-clock subtraction
 * underflow) collapse to "not activated" / progress 0.0. Same
 * rationale as T202 SessionElapsedFormatter and T194
 * LapTimeFormatter — must never silently flip to "yes activated"
 * on bad input.
 *
 * Pure function — no Android, no time source, no I/O — so the
 * Compose layer's clock-source choice (System.currentTimeMillis,
 * SystemClock.uptimeMillis, withFrameNanos) can change without
 * touching the activation/progress contract.
 */
object StopHoldPolicy {
    /**
     * True iff [holdDurationMs] crosses the activation threshold.
     * Inclusive at exactly 500ms; defensive against negatives.
     */
    fun wouldActivate(holdDurationMs: Long): Boolean = holdDurationMs >= HOLD_THRESHOLD_MS

    /**
     * Linear progress 0.0..1.0 of the current hold toward
     * activation. Clamped — past-threshold holds return 1.0 (no
     * overgrown ring); negative holds return 0.0 (no negative arc).
     */
    fun progress(holdDurationMs: Long): Float {
        val clamped = holdDurationMs.coerceIn(0L, HOLD_THRESHOLD_MS)
        return clamped.toFloat() / HOLD_THRESHOLD_MS.toFloat()
    }

    private const val HOLD_THRESHOLD_MS: Long = 500L
}
