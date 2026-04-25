package com.lap.mobile.capture.ble

/**
 * Parsed UBX-ESF-ALG (IMU mount-alignment status) payload (T112).
 * Reports the device's auto-detected installation orientation
 * relative to the vehicle's body frame — useful for forensic
 * audits when the user reports "my acceleration data looks
 * sideways" because the unit was mounted rotated.
 *
 * Yaw/pitch/roll pre-scaled to degrees (raw × 1e-2). Yaw is
 * unsigned [0, 360); pitch and roll are signed.
 */
data class EsfAlg(
    val iTOW: Long,
    val version: Int,
    val flags: Int,
    val error: Int,
    val yawDeg: Double,
    val pitchDeg: Double,
    val rollDeg: Double,
)
