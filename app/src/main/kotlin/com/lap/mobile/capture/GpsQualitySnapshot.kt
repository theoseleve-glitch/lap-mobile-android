package com.lap.mobile.capture

/**
 * Point-in-time GNSS quality reading for the preflight gate (T091).
 *
 * [accuracyMeters] is the most recent fix's reported horizontal
 * accuracy; `null` indicates no fix has been acquired yet (the
 * receiver is still searching). [satelliteCount] is the count of
 * satellites currently in the fix from `GnssStatus.usedInFix`.
 *
 * Mirrors what `LocationManager` + `GnssStatus.Callback` expose on
 * the Android side, so the Hilt-wired impl is a thin adapter.
 */
data class GpsQualitySnapshot(
    val accuracyMeters: Float?,
    val satelliteCount: Int,
)
