package com.lap.mobile.capture.ble

/**
 * Single ESF-RAW measurement entry. Each entry is one IMU sample
 * (accel/gyro per axis, temperature, etc.) with the sensor's own
 * time tag — pre-fusion data the [com.lap.mobile.capture.MotionCaptureService]
 * equivalent for the RaceBox device.
 *
 * [dataType] is the high-byte of the 32-bit `data` field per
 * u-blox spec (e.g., 14 = gyro X, 16 = accel X, …).
 * [dataValue] is the lower 24 bits sign-extended to a signed Int —
 * raw scaled units (mg / mdeg/s / 0.01°C) per type, conversion to
 * the wire schema happens in [RaceboxSampleMapper] (T117).
 */
data class EsfRawMeasurement(
    val dataType: Int,
    val dataValue: Int,
    val sensorTimeTag: Long,
)

/**
 * Parsed UBX-ESF-RAW payload (T111). Variable-length list of
 * [EsfRawMeasurement] entries. The reserved leading word is
 * intentionally not surfaced — u-blox marks it as reserved for
 * future use.
 */
data class EsfRaw(
    val measurements: List<EsfRawMeasurement>,
)
