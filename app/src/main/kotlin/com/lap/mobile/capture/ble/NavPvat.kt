package com.lap.mobile.capture.ble

/**
 * Parsed UBX-NAV-PVAT payload (T109). Same NAV-PVT prefix plus
 * vehicle-attitude tail (roll / pitch / heading + accuracies).
 * RaceBox emits NAV-PVAT when the device's IMU has a fused
 * attitude solution; capture-side consumers use the attitude
 * channels for tilt-aware coaching rules.
 *
 * Attitude angles are pre-converted to degrees (raw is degrees ×
 * 1e-5) — same convention as [NavPvt.headingDeg].
 */
data class NavPvat(
    val iTOW: Long,
    val fixType: Int,
    val numSatellites: Int,
    val longitudeDeg: Double,
    val latitudeDeg: Double,
    val heightMm: Int,
    val velocityNorthMmPerSec: Int,
    val velocityEastMmPerSec: Int,
    val velocityDownMmPerSec: Int,
    val groundSpeedMmPerSec: Int,
    val vehicleRollDeg: Double,
    val vehiclePitchDeg: Double,
    val vehicleHeadingDeg: Double,
    val motionHeadingDeg: Double,
)
