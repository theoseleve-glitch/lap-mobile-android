package com.lap.mobile.capture.ble

import com.lap.mobile.capture.LocationChannels
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

private const val NS_PER_MS: Long = 1_000_000L
private const val EXPECTED_LAT_DEG: Double = 47.123_456_7
private const val EXPECTED_LON_DEG: Double = -122.123_456_7
private const val EXPECTED_HEADING_DEG: Double = 92.345
private const val EXPECTED_HACC_MM: Long = 1_500L
private const val EXPECTED_HACC_METRES: Double = 1.5
private const val EXPECTED_GSPEED_MM_PER_SEC: Int = 12_345
private const val EXPECTED_GSPEED_MPS: Double = 12.345
private const val EXPECTED_HMSL_MM: Int = 12_500
private const val EXPECTED_ALTITUDE_METRES: Double = 12.5

private val FIXTURE_NAV_PVT: NavPvt = NavPvt(
    iTOW = 400_000L,
    year = 2026,
    month = 4,
    day = 25,
    hour = 14,
    minute = 30,
    second = 12,
    fixType = 3,
    numSatellites = 11,
    longitudeDeg = EXPECTED_LON_DEG,
    latitudeDeg = EXPECTED_LAT_DEG,
    heightMm = 14_000,
    heightMslMm = EXPECTED_HMSL_MM,
    horizontalAccuracyMm = EXPECTED_HACC_MM,
    verticalAccuracyMm = 2_000L,
    velocityNorthMmPerSec = 1_000,
    velocityEastMmPerSec = -500,
    velocityDownMmPerSec = 0,
    groundSpeedMmPerSec = EXPECTED_GSPEED_MM_PER_SEC,
    headingDeg = EXPECTED_HEADING_DEG,
)

class RaceboxSampleMapperTest {
    @Test
    fun source_clock_is_racebox_gnss() {
        val sample = FIXTURE_NAV_PVT.toTelemetrySample()

        assertEquals("racebox_gnss", sample.tSourceClock)
    }

    @Test
    fun source_ns_is_utc_epoch_nanos_from_navpvt_date_fields() {
        val expectedEpochMillis = LocalDateTime
            .of(2026, 4, 25, 14, 30, 12)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
        val expectedNanos = expectedEpochMillis * NS_PER_MS

        val sample = FIXTURE_NAV_PVT.toTelemetrySample()

        assertEquals(expectedNanos, sample.tSourceNs)
    }

    @Test
    fun canonical_ns_equals_source_ns_per_adr_007() {
        val sample = FIXTURE_NAV_PVT.toTelemetrySample()

        assertEquals(sample.tSourceNs, sample.tCanonicalNs)
    }

    @Test
    fun channels_carry_lat_lon_degrees() {
        val sample = FIXTURE_NAV_PVT.toTelemetrySample()

        assertEquals(EXPECTED_LAT_DEG, sample.channels[LocationChannels.LATITUDE]!!, 0.0)
        assertEquals(EXPECTED_LON_DEG, sample.channels[LocationChannels.LONGITUDE]!!, 0.0)
    }

    @Test
    fun horizontal_accuracy_converts_mm_to_metres() {
        val sample = FIXTURE_NAV_PVT.toTelemetrySample()

        assertEquals(EXPECTED_HACC_METRES, sample.channels[LocationChannels.ACCURACY_M]!!, 0.0)
    }

    @Test
    fun ground_speed_converts_mm_per_sec_to_mps() {
        val sample = FIXTURE_NAV_PVT.toTelemetrySample()

        assertEquals(EXPECTED_GSPEED_MPS, sample.channels[LocationChannels.SPEED_MPS]!!, 0.0)
    }

    @Test
    fun heading_carries_as_bearing_degrees() {
        val sample = FIXTURE_NAV_PVT.toTelemetrySample()

        assertEquals(EXPECTED_HEADING_DEG, sample.channels[LocationChannels.BEARING_DEG]!!, 0.0)
    }

    @Test
    fun msl_height_carries_as_altitude_metres() {
        val sample = FIXTURE_NAV_PVT.toTelemetrySample()

        assertEquals(EXPECTED_ALTITUDE_METRES, sample.channels[LocationChannels.ALTITUDE_M]!!, 0.0)
    }
}
