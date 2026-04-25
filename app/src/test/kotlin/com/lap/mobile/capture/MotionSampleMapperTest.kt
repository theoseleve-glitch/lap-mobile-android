package com.lap.mobile.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ACCEL_X_MPS2: Float = 0.123f
private const val ACCEL_Y_MPS2: Float = -1.456f
private const val ACCEL_Z_MPS2: Float = 9.802f
private const val GYRO_X_RPS: Float = 0.001f
private const val GYRO_Y_RPS: Float = 0.002f
private const val GYRO_Z_RPS: Float = -0.003f
private const val ACCEL_TS_NS: Long = 1_234_567_890L
private const val GYRO_TS_NS: Long = 1_234_567_891L
private const val FLOAT_TOLERANCE: Double = 0.000_001

/**
 * Wire-shape mapping from on-device [MotionSample] to the server-
 * facing `TelemetrySample` (T069). ADR-007 enforcement points:
 *
 *  - `tSourceClock = "device_monotonic"` for both subtypes (the
 *    accelerometer and gyroscope share the same monotonic clock per
 *    Android's `SensorEvent.timestamp` contract).
 *  - `tSourceNs` is the raw `SensorEvent.timestamp`.
 *  - `tCanonicalNs == tSourceNs` at the mobile seam — no on-device
 *    fusion.
 *  - Channel names are wire-stable; renaming is a coordinated server
 *    change.
 */
class MotionSampleMapperTest {
    @Test
    fun accelerometer_source_clock_is_device_monotonic() {
        assertEquals("device_monotonic", accelSample().toTelemetrySample().tSourceClock)
    }

    @Test
    fun gyroscope_source_clock_is_device_monotonic() {
        assertEquals("device_monotonic", gyroSample().toTelemetrySample().tSourceClock)
    }

    @Test
    fun accelerometer_source_ns_is_raw_sensor_event_timestamp() {
        assertEquals(ACCEL_TS_NS, accelSample().toTelemetrySample().tSourceNs)
    }

    @Test
    fun gyroscope_source_ns_is_raw_sensor_event_timestamp() {
        assertEquals(GYRO_TS_NS, gyroSample().toTelemetrySample().tSourceNs)
    }

    @Test
    fun canonical_ns_equals_source_ns_at_mobile_seam() {
        // ADR-007: clients ship raw, server canonicalizes. Both subtypes obey.
        val accelTs = accelSample().toTelemetrySample()
        val gyroTs = gyroSample().toTelemetrySample()
        assertEquals(accelTs.tSourceNs, accelTs.tCanonicalNs)
        assertEquals(gyroTs.tSourceNs, gyroTs.tCanonicalNs)
    }

    @Test
    fun accelerometer_channels_carry_three_axes() {
        val channels = accelSample().toTelemetrySample().channels
        assertEquals(ACCEL_X_MPS2.toDouble(), channels[MotionChannels.ACCEL_X_MPS2]!!, FLOAT_TOLERANCE)
        assertEquals(ACCEL_Y_MPS2.toDouble(), channels[MotionChannels.ACCEL_Y_MPS2]!!, FLOAT_TOLERANCE)
        assertEquals(ACCEL_Z_MPS2.toDouble(), channels[MotionChannels.ACCEL_Z_MPS2]!!, FLOAT_TOLERANCE)
    }

    @Test
    fun gyroscope_channels_carry_three_axes() {
        val channels = gyroSample().toTelemetrySample().channels
        assertEquals(GYRO_X_RPS.toDouble(), channels[MotionChannels.GYRO_X_RPS]!!, FLOAT_TOLERANCE)
        assertEquals(GYRO_Y_RPS.toDouble(), channels[MotionChannels.GYRO_Y_RPS]!!, FLOAT_TOLERANCE)
        assertEquals(GYRO_Z_RPS.toDouble(), channels[MotionChannels.GYRO_Z_RPS]!!, FLOAT_TOLERANCE)
    }

    @Test
    fun accelerometer_does_not_emit_gyroscope_channels() {
        val channels = accelSample().toTelemetrySample().channels
        assertFalse(MotionChannels.GYRO_X_RPS in channels)
        assertFalse(MotionChannels.GYRO_Y_RPS in channels)
        assertFalse(MotionChannels.GYRO_Z_RPS in channels)
    }

    @Test
    fun gyroscope_does_not_emit_accelerometer_channels() {
        val channels = gyroSample().toTelemetrySample().channels
        assertFalse(MotionChannels.ACCEL_X_MPS2 in channels)
        assertFalse(MotionChannels.ACCEL_Y_MPS2 in channels)
        assertFalse(MotionChannels.ACCEL_Z_MPS2 in channels)
    }

    @Test
    fun accelerometer_emits_exactly_three_channels() {
        // Per-subtype payload must not bleed into the other sensor's keys.
        assertEquals(3, accelSample().toTelemetrySample().channels.size)
    }

    @Test
    fun gyroscope_emits_exactly_three_channels() {
        assertEquals(3, gyroSample().toTelemetrySample().channels.size)
    }

    @Test
    fun channel_names_match_server_ingestion_contract() {
        // Wire-shape lock — server-side ingestion (ADR-012) decodes by these
        // exact strings.
        assertEquals("accel_x_mps2", MotionChannels.ACCEL_X_MPS2)
        assertEquals("accel_y_mps2", MotionChannels.ACCEL_Y_MPS2)
        assertEquals("accel_z_mps2", MotionChannels.ACCEL_Z_MPS2)
        assertEquals("gyro_x_rps", MotionChannels.GYRO_X_RPS)
        assertEquals("gyro_y_rps", MotionChannels.GYRO_Y_RPS)
        assertEquals("gyro_z_rps", MotionChannels.GYRO_Z_RPS)
    }

    @Test
    fun extension_works_through_sealed_parent_reference() {
        // Confirms the orchestrator can map without down-casting per subtype.
        val mixed: List<MotionSample> = listOf(accelSample(), gyroSample())
        val mapped = mixed.map { it.toTelemetrySample() }
        assertEquals(2, mapped.size)
        assertTrue(mapped.all { it.tSourceClock == "device_monotonic" })
    }

    private fun accelSample(): MotionSample.Accelerometer = MotionSample.Accelerometer(
        xMps2 = ACCEL_X_MPS2,
        yMps2 = ACCEL_Y_MPS2,
        zMps2 = ACCEL_Z_MPS2,
        sourceTimestampNs = ACCEL_TS_NS,
    )

    private fun gyroSample(): MotionSample.Gyroscope = MotionSample.Gyroscope(
        xRadPerSec = GYRO_X_RPS,
        yRadPerSec = GYRO_Y_RPS,
        zRadPerSec = GYRO_Z_RPS,
        sourceTimestampNs = GYRO_TS_NS,
    )
}
