package com.lap.mobile.capture.ble

/**
 * Parsed NAV-PVT (position-velocity-time) payload (T107). The
 * minimum field set the capture pipeline cares about — extraneous
 * fields like pDOP / sAcc / headAcc are intentionally absent
 * because the upstream consumer ([RaceboxSampleMapper], T117) only
 * keys off position / velocity / time / fix quality. Adding fields
 * later means a single edit here + one test fixture extension.
 *
 * Position fields are pre-converted into the units the rest of the
 * mobile pipeline uses:
 *  - [latitudeDeg] / [longitudeDeg]: degrees (raw NAV-PVT is
 *    degrees × 1e-7).
 *  - [headingDeg]: degrees (raw is degrees × 1e-5).
 *  - All other distances stay in raw mm and velocities in mm/s —
 *    [com.lap.mobile.capture.LocationSampleMapper] et al. expect
 *    metres / m/s and convert downstream.
 */
data class NavPvt(
    val iTOW: Long,
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int,
    val fixType: Int,
    val numSatellites: Int,
    val longitudeDeg: Double,
    val latitudeDeg: Double,
    val heightMm: Int,
    val heightMslMm: Int,
    val horizontalAccuracyMm: Long,
    val verticalAccuracyMm: Long,
    val velocityNorthMmPerSec: Int,
    val velocityEastMmPerSec: Int,
    val velocityDownMmPerSec: Int,
    val groundSpeedMmPerSec: Int,
    val headingDeg: Double,
)
