package com.lap.mobile.capture

import com.lap.schemas.v1.TelemetrySample

/**
 * `tSourceClock` literal for IMU samples. `SensorEvent.timestamp` is
 * `nanoseconds since boot` (Android's `SystemClock.elapsedRealtimeNanos`),
 * which the ADR-007 source-clock vocabulary names `device_monotonic`.
 */
private const val SOURCE_CLOCK_DEVICE_MONOTONIC: String = "device_monotonic"

/**
 * On-device wire-shape mapping: [MotionSample] → [TelemetrySample]
 * (T069). Single extension on the sealed parent so the orchestrator
 * (T071) can map a mixed Accelerometer/Gyroscope flow without down-
 * casting per subtype.
 *
 * Per ADR-007, both subtypes ship raw `SensorEvent.timestamp` as
 * `tSourceNs` and set `tCanonicalNs == tSourceNs`; canonical
 * alignment is server-side. Channel payload is partitioned:
 * Accelerometer carries only the `accel_*_mps2` keys, Gyroscope only
 * the `gyro_*_rps` keys — neither subtype emits both.
 */
fun MotionSample.toTelemetrySample(): TelemetrySample = TelemetrySample(
    tSourceNs = sourceTimestampNs,
    tCanonicalNs = sourceTimestampNs,
    tSourceClock = SOURCE_CLOCK_DEVICE_MONOTONIC,
    channels = when (this) {
        is MotionSample.Accelerometer -> mapOf(
            MotionChannels.ACCEL_X_MPS2 to xMps2.toDouble(),
            MotionChannels.ACCEL_Y_MPS2 to yMps2.toDouble(),
            MotionChannels.ACCEL_Z_MPS2 to zMps2.toDouble(),
        )
        is MotionSample.Gyroscope -> mapOf(
            MotionChannels.GYRO_X_RPS to xRadPerSec.toDouble(),
            MotionChannels.GYRO_Y_RPS to yRadPerSec.toDouble(),
            MotionChannels.GYRO_Z_RPS to zRadPerSec.toDouble(),
        )
    },
)
