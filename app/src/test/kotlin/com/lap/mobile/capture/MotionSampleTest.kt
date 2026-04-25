package com.lap.mobile.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

private const val ACCEL_X_MPS2: Float = 0.123f
private const val ACCEL_Y_MPS2: Float = -1.456f
private const val ACCEL_Z_MPS2: Float = 9.802f
private const val GYRO_X_RAD_S: Float = 0.001f
private const val GYRO_Y_RAD_S: Float = 0.002f
private const val GYRO_Z_RAD_S: Float = -0.003f
private const val ACCEL_TS_NS: Long = 1_234_567_890L
private const val GYRO_TS_NS: Long = 1_234_567_891L

/**
 * Field-set lock for [MotionSample] (T068). T069 maps the two
 * subtypes onto the shared `TelemetrySample` shape with
 * `t_source_clock="device_monotonic"` per ADR-007 — changing this
 * field set means changing the wire shape, so the test exists to
 * make that change deliberate.
 */
class MotionSampleTest {
    @Test
    fun accelerometer_carries_three_axes_in_meters_per_second_squared() {
        val sample = MotionSample.Accelerometer(
            xMps2 = ACCEL_X_MPS2,
            yMps2 = ACCEL_Y_MPS2,
            zMps2 = ACCEL_Z_MPS2,
            sourceTimestampNs = ACCEL_TS_NS,
        )
        assertEquals(ACCEL_X_MPS2, sample.xMps2)
        assertEquals(ACCEL_Y_MPS2, sample.yMps2)
        assertEquals(ACCEL_Z_MPS2, sample.zMps2)
        assertEquals(ACCEL_TS_NS, sample.sourceTimestampNs)
    }

    @Test
    fun gyroscope_carries_three_axes_in_radians_per_second() {
        val sample = MotionSample.Gyroscope(
            xRadPerSec = GYRO_X_RAD_S,
            yRadPerSec = GYRO_Y_RAD_S,
            zRadPerSec = GYRO_Z_RAD_S,
            sourceTimestampNs = GYRO_TS_NS,
        )
        assertEquals(GYRO_X_RAD_S, sample.xRadPerSec)
        assertEquals(GYRO_Y_RAD_S, sample.yRadPerSec)
        assertEquals(GYRO_Z_RAD_S, sample.zRadPerSec)
        assertEquals(GYRO_TS_NS, sample.sourceTimestampNs)
    }

    @Test
    fun accelerometer_and_gyroscope_are_distinct_subtypes() {
        val accel: MotionSample = MotionSample.Accelerometer(
            xMps2 = ACCEL_X_MPS2,
            yMps2 = ACCEL_Y_MPS2,
            zMps2 = ACCEL_Z_MPS2,
            sourceTimestampNs = ACCEL_TS_NS,
        )
        val gyro: MotionSample = MotionSample.Gyroscope(
            xRadPerSec = GYRO_X_RAD_S,
            yRadPerSec = GYRO_Y_RAD_S,
            zRadPerSec = GYRO_Z_RAD_S,
            sourceTimestampNs = GYRO_TS_NS,
        )
        // Sealed pattern lets the mapper (T069) and orchestrator (T071)
        // route per subtype without a discriminator field.
        assertNotEquals(accel::class, gyro::class)
    }

    @Test
    fun source_timestamp_is_a_long_nanos_value() {
        val sample = MotionSample.Accelerometer(
            xMps2 = ACCEL_X_MPS2,
            yMps2 = ACCEL_Y_MPS2,
            zMps2 = ACCEL_Z_MPS2,
            sourceTimestampNs = ACCEL_TS_NS,
        )
        // SensorEvent.timestamp is `nanoseconds since boot`; storing as Long
        // matches that quantity directly.
        val ts: Long = sample.sourceTimestampNs
        assertEquals(ACCEL_TS_NS, ts)
    }
}
