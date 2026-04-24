package com.lap.mobile.capture

private const val LAT_MIN: Double = -90.0
private const val LAT_MAX: Double = 90.0
private const val LON_MIN: Double = -180.0
private const val LON_MAX: Double = 180.0
private const val BEARING_MIN: Float = 0.0f
private const val BEARING_MAX_EXCLUSIVE: Float = 360.0f
private const val ZERO_FLOAT: Float = 0.0f

/**
 * Plausibility filter for [LocationSample]s (T066). Returns the set
 * of [LocationFieldViolation]s a sample triggers — empty == valid.
 *
 * Bounds enforced:
 *  - latitude  ∈ [-90, 90]      (closed; both poles are valid)
 *  - longitude ∈ [-180, 180]    (closed; antimeridian valid either way)
 *  - accuracy  > 0              (Android FLPC: 0 == "no estimate")
 *  - speed     ≥ 0              (only checked when present)
 *  - bearing   ∈ [0, 360)       (half-open; 360° wraps to 0°)
 *
 * Accuracy-threshold-for-go (`≤ 20 m`) is intentionally NOT here —
 * that's T091's preflight gate and depends on per-track tolerance.
 */
class LocationSampleValidator {
    fun violations(sample: LocationSample): Set<LocationFieldViolation> = buildSet {
        if (sample.latitude !in LAT_MIN..LAT_MAX) add(LocationFieldViolation.LATITUDE_OUT_OF_RANGE)
        if (sample.longitude !in LON_MIN..LON_MAX) add(LocationFieldViolation.LONGITUDE_OUT_OF_RANGE)
        if (sample.accuracyMeters <= ZERO_FLOAT) add(LocationFieldViolation.ACCURACY_NOT_POSITIVE)
        sample.speedMps?.let { if (it < ZERO_FLOAT) add(LocationFieldViolation.SPEED_NEGATIVE) }
        sample.bearingDeg?.let {
            if (it < BEARING_MIN || it >= BEARING_MAX_EXCLUSIVE) {
                add(LocationFieldViolation.BEARING_OUT_OF_RANGE)
            }
        }
    }

    fun isValid(sample: LocationSample): Boolean = violations(sample).isEmpty()
}
