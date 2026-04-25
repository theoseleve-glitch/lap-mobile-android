package com.lap.mobile.dashboard

/**
 * Minimal geographic point for the dashboard's track-map kernel
 * (T195).
 *
 * Deliberately decoupled from sensor-side types like
 * [com.lap.mobile.capture.ble.NavPvt] — the dashboard's projection
 * math operates on lat/lon only, doesn't need fix quality, satellite
 * count, height, or velocity. A future converter mapping
 * NavPvt → GpsPoint stays out of this kernel so JVM tests don't
 * have to construct full sensor DTOs.
 */
data class GpsPoint(
    val latitudeDeg: Double,
    val longitudeDeg: Double,
)
