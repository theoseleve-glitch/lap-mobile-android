package com.lap.mobile.dashboard

/**
 * GPS fix quality band for the dashboard's red/yellow/green dot
 * indicator (T198).
 *
 * Three discrete states matching driver expectations:
 *   - [Good]:     trust the live data — breadcrumb fits the lane,
 *                 lap-time numerics are reliable.
 *   - [Marginal]: data flowing but degraded — breadcrumb may
 *                 visibly wander, lap-time deltas should be taken
 *                 with caution.
 *   - [NoFix]:    no positional data (or dead-reckoning-only,
 *                 which drifts unbounded) — capture continues but
 *                 the dashboard's live readouts won't update from
 *                 GPS.
 */
enum class GpsFixQuality {
    Good,
    Marginal,
    NoFix,
}

/**
 * Inputs for [GpsFixQualityClassifier]. Decoupled from sensor-side
 * NavPvt so the kernel doesn't depend on the BLE pipeline.
 *
 *   - [fixType]: u-blox-style fix type code (0=none, 1=DR-only,
 *     2=2D, 3=3D, 4=GNSS+DR, 5=time-only).
 *   - [numSatellites]: count of satellites contributing to the fix.
 *   - [horizontalAccuracyMm]: u-blox NAV-PVT horizontal accuracy
 *     estimate in millimeters (1 sigma).
 */
data class GpsFixQualityInputs(
    val fixType: Int,
    val numSatellites: Int,
    val horizontalAccuracyMm: Long,
)
