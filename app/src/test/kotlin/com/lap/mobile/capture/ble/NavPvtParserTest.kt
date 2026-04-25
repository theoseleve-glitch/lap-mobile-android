package com.lap.mobile.capture.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val FIXTURE_ITOW: Long = 123_456_789L
private const val FIXTURE_YEAR: Int = 2026
private const val FIXTURE_MONTH: Int = 4
private const val FIXTURE_DAY: Int = 25
private const val FIXTURE_HOUR: Int = 14
private const val FIXTURE_MIN: Int = 23
private const val FIXTURE_SEC: Int = 45
private const val FIXTURE_FIX_TYPE: Int = 3
private const val FIXTURE_NUM_SV: Int = 12
private const val FIXTURE_LON_RAW: Int = 60_260_180
private const val FIXTURE_LAT_RAW: Int = 476_432_220
private const val FIXTURE_HEIGHT_MM: Int = 250_000
private const val FIXTURE_HMSL_MM: Int = 245_000
private const val FIXTURE_HACC_MM: Long = 3_500L
private const val FIXTURE_VACC_MM: Long = 5_200L
private const val FIXTURE_VEL_N: Int = 12_345
private const val FIXTURE_VEL_E: Int = -6_789
private const val FIXTURE_VEL_D: Int = 100
private const val FIXTURE_GSPEED: Int = 14_000
private const val FIXTURE_HEAD_RAW: Int = 9_000_000
private const val EXPECTED_LON_DEG: Double = 6.026_018
private const val EXPECTED_LAT_DEG: Double = 47.643_222
private const val EXPECTED_HEAD_DEG: Double = 90.0
private const val FLOAT_TOLERANCE: Double = 1e-7

/**
 * Unit tests for [NavPvtParser] (T107). Synthesises NAV-PVT
 * payloads matching the u-blox spec layout and verifies the
 * parser extracts every position / velocity / time field with the
 * right unit conversions applied.
 *
 * "Real fixture" in the task acceptance — captured RaceBox logs
 * aren't checked into this repo, so the fixture is constructed from
 * the spec; the fixture builder helper double-as documentation of
 * the wire layout.
 */
class NavPvtParserTest {
    @Test
    fun parses_full_navpvt_fixture() {
        val payload = buildNavPvtPayload()

        val parsed = NavPvtParser.parse(payload)

        requireNotNull(parsed) { "parser must accept canonical 92-byte NAV-PVT payload" }
        assertEquals(FIXTURE_ITOW, parsed.iTOW)
        assertEquals(FIXTURE_YEAR, parsed.year)
        assertEquals(FIXTURE_MONTH, parsed.month)
        assertEquals(FIXTURE_DAY, parsed.day)
        assertEquals(FIXTURE_HOUR, parsed.hour)
        assertEquals(FIXTURE_MIN, parsed.minute)
        assertEquals(FIXTURE_SEC, parsed.second)
        assertEquals(FIXTURE_FIX_TYPE, parsed.fixType)
        assertEquals(FIXTURE_NUM_SV, parsed.numSatellites)
        assertEquals(EXPECTED_LON_DEG, parsed.longitudeDeg, FLOAT_TOLERANCE)
        assertEquals(EXPECTED_LAT_DEG, parsed.latitudeDeg, FLOAT_TOLERANCE)
        assertEquals(FIXTURE_HEIGHT_MM, parsed.heightMm)
        assertEquals(FIXTURE_HMSL_MM, parsed.heightMslMm)
        assertEquals(FIXTURE_HACC_MM, parsed.horizontalAccuracyMm)
        assertEquals(FIXTURE_VACC_MM, parsed.verticalAccuracyMm)
        assertEquals(FIXTURE_VEL_N, parsed.velocityNorthMmPerSec)
        assertEquals(FIXTURE_VEL_E, parsed.velocityEastMmPerSec)
        assertEquals(FIXTURE_VEL_D, parsed.velocityDownMmPerSec)
        assertEquals(FIXTURE_GSPEED, parsed.groundSpeedMmPerSec)
        assertEquals(EXPECTED_HEAD_DEG, parsed.headingDeg, FLOAT_TOLERANCE)
    }

    @Test
    fun returns_null_for_too_short_payload() {
        val payload = ByteArray(NavPvtParser.PAYLOAD_SIZE - 1)

        assertNull(NavPvtParser.parse(payload))
    }

    @Test
    fun returns_null_for_too_long_payload() {
        val payload = ByteArray(NavPvtParser.PAYLOAD_SIZE + 1)

        assertNull(NavPvtParser.parse(payload))
    }

    @Test
    fun returns_null_for_empty_payload() {
        assertNull(NavPvtParser.parse(ByteArray(0)))
    }

    /**
     * Build a 92-byte NAV-PVT payload matching the u-blox spec
     * layout. Field offsets per UBX-NAV-PVT documentation.
     */
    @Suppress("LongMethod")
    private fun buildNavPvtPayload(): ByteArray {
        val buffer = ByteBuffer.allocate(NavPvtParser.PAYLOAD_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(FIXTURE_ITOW.toInt()) // 0  iTOW (U4)
        buffer.putShort(FIXTURE_YEAR.toShort()) // 4  year (U2)
        buffer.put(FIXTURE_MONTH.toByte()) // 6  month (U1)
        buffer.put(FIXTURE_DAY.toByte()) // 7  day (U1)
        buffer.put(FIXTURE_HOUR.toByte()) // 8  hour (U1)
        buffer.put(FIXTURE_MIN.toByte()) // 9  min (U1)
        buffer.put(FIXTURE_SEC.toByte()) // 10 sec (U1)
        buffer.put(0) // 11 valid (X1)
        buffer.putInt(0) // 12 tAcc (U4)
        buffer.putInt(0) // 16 nano (I4)
        buffer.put(FIXTURE_FIX_TYPE.toByte()) // 20 fixType (U1)
        buffer.put(0) // 21 flags (X1)
        buffer.put(0) // 22 flags2 (X1)
        buffer.put(FIXTURE_NUM_SV.toByte()) // 23 numSV (U1)
        buffer.putInt(FIXTURE_LON_RAW) // 24 lon (I4, deg × 1e-7)
        buffer.putInt(FIXTURE_LAT_RAW) // 28 lat (I4, deg × 1e-7)
        buffer.putInt(FIXTURE_HEIGHT_MM) // 32 height (I4, mm)
        buffer.putInt(FIXTURE_HMSL_MM) // 36 hMSL  (I4, mm)
        buffer.putInt(FIXTURE_HACC_MM.toInt()) // 40 hAcc (U4, mm)
        buffer.putInt(FIXTURE_VACC_MM.toInt()) // 44 vAcc (U4, mm)
        buffer.putInt(FIXTURE_VEL_N) // 48 velN (I4, mm/s)
        buffer.putInt(FIXTURE_VEL_E) // 52 velE (I4, mm/s)
        buffer.putInt(FIXTURE_VEL_D) // 56 velD (I4, mm/s)
        buffer.putInt(FIXTURE_GSPEED) // 60 gSpeed (I4, mm/s)
        buffer.putInt(FIXTURE_HEAD_RAW) // 64 headMot (I4, deg × 1e-5)
        buffer.putInt(0) // 68 sAcc (U4)
        buffer.putInt(0) // 72 headAcc (U4)
        buffer.putShort(0) // 76 pDOP (U2)
        repeat(6) { buffer.put(0) } // 78-83 flags3 + reserved
        buffer.putInt(0) // 84 headVeh (I4)
        buffer.putShort(0) // 88 magDec (I2)
        buffer.putShort(0) // 90 magAcc (U2)
        return buffer.array()
    }
}
