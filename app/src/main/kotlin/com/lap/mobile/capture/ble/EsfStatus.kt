package com.lap.mobile.capture.ble

/**
 * Status of one external sensor as reported in UBX-ESF-STATUS.
 *
 * [sensStatus1] / [sensStatus2] are bit-fields per the u-blox spec
 * (sensor type, used-for-fusion bit, ready bit, calibration status,
 * etc.); we surface them raw so future code can decode whichever
 * subfields it needs without reshaping the parser.
 */
data class EsfSensorStatus(
    val sensStatus1: Int,
    val sensStatus2: Int,
    val frequencyHz: Int,
    val faults: Int,
)

/**
 * Parsed UBX-ESF-STATUS payload (T114). Reports the device-level
 * fusion state and per-sensor health that the recording UI's
 * connection-detail surface (post-Block J) can render to give the
 * user "your IMU is calibrating" feedback.
 */
data class EsfStatus(
    val iTOW: Long,
    val version: Int,
    val fusionMode: Int,
    val sensors: List<EsfSensorStatus>,
)
