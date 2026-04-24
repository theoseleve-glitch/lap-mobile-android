package com.lap.mobile.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val LATITUDE_FIXTURE: Double = 47.643_222
private const val LONGITUDE_FIXTURE: Double = 6.026_018
private const val ACCURACY_M_FIXTURE: Float = 3.5f
private const val ALTITUDE_M_FIXTURE: Double = 612.4
private const val SPEED_MPS_FIXTURE: Float = 38.7f
private const val BEARING_DEG_FIXTURE: Float = 187.2f
private const val SOURCE_TS_NS_FIXTURE: Long = 1_234_567_890L
private const val RECEIPT_MS_FIXTURE: Long = 1_745_487_600_123L
private const val FLOAT_TOLERANCE: Double = 0.001

/**
 * The wire-shape mapping from on-device [LocationSample] to the server-
 * facing `TelemetrySample` (T065). ADR-007 is what this test enforces:
 *
 *  - `tSourceClock = "gnss"` always for FLPC-derived samples.
 *  - `tSourceNs` is the raw `Location.elapsedRealtimeNanos`, never
 *    transformed on-device.
 *  - `tCanonicalNs == tSourceNs` at the mobile seam — the server
 *    aligns; clients do not pre-fuse.
 *  - Channel names match the server ingestion contract literal-for-
 *    literal; renaming a channel here is a wire break.
 */
class LocationSampleMapperTest {
    private val sample: LocationSample = LocationSample(
        latitude = LATITUDE_FIXTURE,
        longitude = LONGITUDE_FIXTURE,
        accuracyMeters = ACCURACY_M_FIXTURE,
        altitudeMeters = ALTITUDE_M_FIXTURE,
        speedMps = SPEED_MPS_FIXTURE,
        bearingDeg = BEARING_DEG_FIXTURE,
        sourceTimestampNs = SOURCE_TS_NS_FIXTURE,
        receiptUtcMillis = RECEIPT_MS_FIXTURE,
    )

    @Test
    fun source_clock_is_gnss() {
        assertEquals("gnss", sample.toTelemetrySample().tSourceClock)
    }

    @Test
    fun source_ns_is_raw_device_clock() {
        assertEquals(SOURCE_TS_NS_FIXTURE, sample.toTelemetrySample().tSourceNs)
    }

    @Test
    fun canonical_ns_equals_source_ns_on_mobile_seam() {
        // ADR-007: mobile ships raw, server canonicalizes. Pre-fusing on-device
        // would corrupt forensic debugging.
        val ts = sample.toTelemetrySample()
        assertEquals(ts.tSourceNs, ts.tCanonicalNs)
    }

    @Test
    fun channels_carry_required_position_and_accuracy() {
        val channels = sample.toTelemetrySample().channels
        assertEquals(LATITUDE_FIXTURE, channels[LocationChannels.LATITUDE]!!, 0.0)
        assertEquals(LONGITUDE_FIXTURE, channels[LocationChannels.LONGITUDE]!!, 0.0)
        assertEquals(
            ACCURACY_M_FIXTURE.toDouble(),
            channels[LocationChannels.ACCURACY_M]!!,
            FLOAT_TOLERANCE,
        )
    }

    @Test
    fun channels_include_optional_kinematics_when_present() {
        val channels = sample.toTelemetrySample().channels
        assertEquals(SPEED_MPS_FIXTURE.toDouble(), channels[LocationChannels.SPEED_MPS]!!, FLOAT_TOLERANCE)
        assertEquals(BEARING_DEG_FIXTURE.toDouble(), channels[LocationChannels.BEARING_DEG]!!, FLOAT_TOLERANCE)
        assertEquals(ALTITUDE_M_FIXTURE, channels[LocationChannels.ALTITUDE_M]!!, 0.0)
    }

    @Test
    fun channels_omit_optional_kinematics_when_absent() {
        val noKinematics = sample.copy(
            speedMps = null,
            bearingDeg = null,
            altitudeMeters = null,
        )
        val channels = noKinematics.toTelemetrySample().channels
        // Map<String, Double> can't hold null — absence by omission lets the
        // server distinguish "not measured" from "measured = 0".
        assertFalse(LocationChannels.SPEED_MPS in channels)
        assertFalse(LocationChannels.BEARING_DEG in channels)
        assertFalse(LocationChannels.ALTITUDE_M in channels)
    }

    @Test
    fun required_channels_remain_present_when_optionals_absent() {
        val noKinematics = sample.copy(
            speedMps = null,
            bearingDeg = null,
            altitudeMeters = null,
        )
        val channels = noKinematics.toTelemetrySample().channels
        assertTrue(LocationChannels.LATITUDE in channels)
        assertTrue(LocationChannels.LONGITUDE in channels)
        assertTrue(LocationChannels.ACCURACY_M in channels)
    }

    @Test
    fun channel_names_match_server_ingestion_contract() {
        // Wire-shape lock: server ingestion looks up channels by these literal
        // strings (ADR-012). Renaming on either side is a wire break.
        assertEquals("lat", LocationChannels.LATITUDE)
        assertEquals("lon", LocationChannels.LONGITUDE)
        assertEquals("accuracy_m", LocationChannels.ACCURACY_M)
        assertEquals("speed_mps", LocationChannels.SPEED_MPS)
        assertEquals("bearing_deg", LocationChannels.BEARING_DEG)
        assertEquals("altitude_m", LocationChannels.ALTITUDE_M)
    }
}
