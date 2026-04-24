package com.lap.mobile.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val LATITUDE: Double = 47.643_222
private const val LONGITUDE: Double = 6.026_018
private const val ACCURACY_M: Float = 3.5f
private const val ALTITUDE_M: Double = 612.4
private const val SPEED_MPS: Float = 38.7f
private const val BEARING_DEG: Float = 187.2f
private const val SOURCE_TS_NS: Long = 1_234_567_890_123L
private const val RECEIPT_MS: Long = 1_745_487_600_123L

/**
 * Field-set lock for [LocationSample]. T065 maps these fields onto the
 * shared `TelemetrySample` shape (with `t_source_clock="gnss"` per
 * ADR-007), so changing the contract here means changing the wire
 * shape — the test exists to make that change deliberate, not
 * accidental.
 */
class LocationSampleTest {
    private val sample: LocationSample = LocationSample(
        latitude = LATITUDE,
        longitude = LONGITUDE,
        accuracyMeters = ACCURACY_M,
        altitudeMeters = ALTITUDE_M,
        speedMps = SPEED_MPS,
        bearingDeg = BEARING_DEG,
        sourceTimestampNs = SOURCE_TS_NS,
        receiptUtcMillis = RECEIPT_MS,
    )

    @Test
    fun carries_position_and_accuracy() {
        assertEquals(LATITUDE, sample.latitude, 0.0)
        assertEquals(LONGITUDE, sample.longitude, 0.0)
        assertEquals(ACCURACY_M, sample.accuracyMeters)
    }

    @Test
    fun carries_optional_kinematics() {
        assertEquals(ALTITUDE_M, sample.altitudeMeters)
        assertEquals(SPEED_MPS, sample.speedMps)
        assertEquals(BEARING_DEG, sample.bearingDeg)
    }

    @Test
    fun optional_kinematics_can_be_absent() {
        val noKinematics = sample.copy(altitudeMeters = null, speedMps = null, bearingDeg = null)
        assertNull(noKinematics.altitudeMeters)
        assertNull(noKinematics.speedMps)
        assertNull(noKinematics.bearingDeg)
    }

    @Test
    fun carries_dual_timestamps_for_adr_007() {
        // sourceTimestampNs is the raw sensor clock per ADR-007 — server canonicalizes.
        assertEquals(SOURCE_TS_NS, sample.sourceTimestampNs)
        // receiptUtcMillis anchors the canonical timeline at upload time.
        assertEquals(RECEIPT_MS, sample.receiptUtcMillis)
        assertNotEquals(
            "two timestamps must be distinct quantities, not aliases",
            sample.sourceTimestampNs,
            sample.receiptUtcMillis,
        )
    }
}
