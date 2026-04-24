package com.lap.mobile.capture

/**
 * Typed plausibility-violations [LocationSampleValidator] reports
 * (T066). One enum value per field-level rule so the upstream filter
 * can log distinct counters and the downstream UI can surface a
 * specific cause when a sample is rejected.
 *
 * Physical bounds only — accuracy-threshold checks for "is the fix
 * good enough to start recording?" live in T091's preflight gate.
 */
enum class LocationFieldViolation {
    LATITUDE_OUT_OF_RANGE,
    LONGITUDE_OUT_OF_RANGE,
    ACCURACY_NOT_POSITIVE,
    SPEED_NEGATIVE,
    BEARING_OUT_OF_RANGE,
}
