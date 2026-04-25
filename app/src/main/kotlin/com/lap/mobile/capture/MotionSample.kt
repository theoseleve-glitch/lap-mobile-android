package com.lap.mobile.capture

/**
 * Raw motion sample from the device IMU (T068). The two subtypes
 * mirror the two distinct sensor streams:
 *
 *  - [Accelerometer] — m/s² on three orthogonal axes
 *  - [Gyroscope]     — rad/s on three orthogonal axes
 *
 * Each `SensorEvent` fires independently with its own timestamp, so
 * keeping the streams as separate sealed-subtype values respects
 * ADR-007's "no on-device fusion" rule. Channel mapping (T069) routes
 * per subtype into `TelemetrySample` with `t_source_clock="device_monotonic"`.
 *
 * [sourceTimestampNs] is `SensorEvent.timestamp` — nanoseconds since
 * device boot per the Android docs. Carried as `Long` so no precision
 * is lost in the eventual server canonicalization.
 */
sealed class MotionSample {
    abstract val sourceTimestampNs: Long

    data class Accelerometer(
        val xMps2: Float,
        val yMps2: Float,
        val zMps2: Float,
        override val sourceTimestampNs: Long,
    ) : MotionSample()

    data class Gyroscope(
        val xRadPerSec: Float,
        val yRadPerSec: Float,
        val zRadPerSec: Float,
        override val sourceTimestampNs: Long,
    ) : MotionSample()
}
