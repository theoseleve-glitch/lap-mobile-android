package com.lap.mobile.dashboard

/**
 * Pure-function classifier: GPS fix metrics → [GpsFixQuality]
 * (T198).
 *
 * v1 thresholds tuned for circuit racing:
 *   - [GpsFixQuality.Good]: fixType ≥ 3D AND ≥6 satellites AND
 *     horizontalAccuracy ≤ 5 m. The breadcrumb visibly fits the
 *     lane; lap-time numerics are reliable.
 *   - [GpsFixQuality.Marginal]: fixType ≥ 2D AND not-Good. Data
 *     flowing but degraded — 2D-only, too-few-satellites (3D fix
 *     can jump when one drops out of view), or accuracy beyond 5 m
 *     (breadcrumb may wander outside lane width).
 *   - [GpsFixQuality.NoFix]: fixType < 2D. No fix or
 *     dead-reckoning-only (DR drifts unbounded without GPS
 *     corrections, so the dashboard cares about position truth not
 *     last-known-position).
 *
 * fixType ≥ 3 covers both pure 3D (3) and GNSS+DR (4); future
 * fixType codes (e.g., dual-frequency, RTK) won't need code
 * changes here as long as they're encoded as ≥3.
 *
 * Pure function — no Android, no time, no I/O — so the contract is
 * locked by JVM unit tests and the Android shell can't accidentally
 * change semantics by changing how it observes its environment.
 */
object GpsFixQualityClassifier {
    fun classify(inputs: GpsFixQualityInputs): GpsFixQuality {
        if (inputs.fixType < FIX_TYPE_2D_MIN) return GpsFixQuality.NoFix
        val isGood =
            inputs.fixType >= FIX_TYPE_3D_MIN &&
                inputs.numSatellites >= MIN_GOOD_SATELLITES &&
                inputs.horizontalAccuracyMm <= MAX_GOOD_ACCURACY_MM
        return if (isGood) GpsFixQuality.Good else GpsFixQuality.Marginal
    }

    private const val FIX_TYPE_2D_MIN: Int = 2
    private const val FIX_TYPE_3D_MIN: Int = 3
    private const val MIN_GOOD_SATELLITES: Int = 6
    private const val MAX_GOOD_ACCURACY_MM: Long = 5_000L
}
