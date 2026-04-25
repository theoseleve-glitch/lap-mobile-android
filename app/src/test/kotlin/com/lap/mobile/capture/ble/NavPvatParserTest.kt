package com.lap.mobile.capture.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val FIXTURE_ITOW: Long = 200_000L
private const val FIXTURE_FIX_TYPE: Int = 3
private const val FIXTURE_NUM_SV: Int = 14
private const val FIXTURE_LON_RAW: Int = 60_260_180
private const val FIXTURE_LAT_RAW: Int = 476_432_220
private const val FIXTURE_HEIGHT_MM: Int = 250_000
private const val FIXTURE_VEL_N: Int = 5_000
private const val FIXTURE_VEL_E: Int = -2_000
private const val FIXTURE_VEL_D: Int = 50
private const val FIXTURE_GSPEED: Int = 5_400
private const val FIXTURE_ROLL_RAW: Int = 250_000
private const val FIXTURE_PITCH_RAW: Int = -150_000
private const val FIXTURE_HEAD_VEH_RAW: Int = 9_000_000
private const val FIXTURE_HEAD_MOT_RAW: Int = 9_100_000
private const val EXPECTED_LON_DEG: Double = 6.026_018
private const val EXPECTED_LAT_DEG: Double = 47.643_222
private const val EXPECTED_ROLL_DEG: Double = 2.5
private const val EXPECTED_PITCH_DEG: Double = -1.5
private const val EXPECTED_HEAD_VEH_DEG: Double = 90.0
private const val EXPECTED_HEAD_MOT_DEG: Double = 91.0
private const val FLOAT_TOLERANCE: Double = 1e-7

class NavPvatParserTest {
    @Test
    fun parses_full_navpvat_fixture() {
        val payload = buildPayload()

        val parsed = requireNotNull(NavPvatParser.parse(payload))

        assertEquals(FIXTURE_ITOW, parsed.iTOW)
        assertEquals(FIXTURE_FIX_TYPE, parsed.fixType)
        assertEquals(FIXTURE_NUM_SV, parsed.numSatellites)
        assertEquals(EXPECTED_LON_DEG, parsed.longitudeDeg, FLOAT_TOLERANCE)
        assertEquals(EXPECTED_LAT_DEG, parsed.latitudeDeg, FLOAT_TOLERANCE)
        assertEquals(FIXTURE_HEIGHT_MM, parsed.heightMm)
        assertEquals(FIXTURE_VEL_N, parsed.velocityNorthMmPerSec)
        assertEquals(FIXTURE_VEL_E, parsed.velocityEastMmPerSec)
        assertEquals(FIXTURE_VEL_D, parsed.velocityDownMmPerSec)
        assertEquals(FIXTURE_GSPEED, parsed.groundSpeedMmPerSec)
        assertEquals(EXPECTED_ROLL_DEG, parsed.vehicleRollDeg, FLOAT_TOLERANCE)
        assertEquals(EXPECTED_PITCH_DEG, parsed.vehiclePitchDeg, FLOAT_TOLERANCE)
        assertEquals(EXPECTED_HEAD_VEH_DEG, parsed.vehicleHeadingDeg, FLOAT_TOLERANCE)
        assertEquals(EXPECTED_HEAD_MOT_DEG, parsed.motionHeadingDeg, FLOAT_TOLERANCE)
    }

    @Test
    fun returns_null_for_wrong_size() {
        assertNull(NavPvatParser.parse(ByteArray(NavPvatParser.PAYLOAD_SIZE - 1)))
        assertNull(NavPvatParser.parse(ByteArray(NavPvatParser.PAYLOAD_SIZE + 1)))
        assertNull(NavPvatParser.parse(ByteArray(0)))
    }

    @Suppress("LongMethod")
    private fun buildPayload(): ByteArray {
        val buffer = ByteBuffer.allocate(NavPvatParser.PAYLOAD_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        // Offset 0-19: PVT-style prefix (iTOW, version, valid, year/month/day/hour/min/sec, reserved, tAcc, nano)
        buffer.putInt(FIXTURE_ITOW.toInt()) // 0  iTOW
        buffer.put(0) // 4  version
        buffer.put(0) // 5  valid
        buffer.putShort(0) // 6  year
        buffer.put(0) // 8  month
        buffer.put(0) // 9  day
        buffer.put(0) // 10 hour
        buffer.put(0) // 11 min
        buffer.put(0) // 12 sec
        buffer.put(0) // 13 reserved
        buffer.putInt(0) // 14-17 tAcc
        // pad to 20
        buffer.put(0)
        buffer.put(0)
        // Offset 20: fixType
        buffer.put(FIXTURE_FIX_TYPE.toByte()) // 20 fixType
        buffer.put(0) // 21 flags
        buffer.put(0) // 22 flags2
        buffer.put(FIXTURE_NUM_SV.toByte()) // 23 numSV
        // Offset 24: lon, lat, height, hMSL
        buffer.putInt(FIXTURE_LON_RAW) // 24
        buffer.putInt(FIXTURE_LAT_RAW) // 28
        buffer.putInt(FIXTURE_HEIGHT_MM) // 32
        buffer.putInt(0) // 36 hMSL
        buffer.putInt(0) // 40 hAcc
        buffer.putInt(0) // 44 vAcc
        buffer.putInt(FIXTURE_VEL_N) // 48
        buffer.putInt(FIXTURE_VEL_E) // 52
        buffer.putInt(FIXTURE_VEL_D) // 56
        buffer.putInt(FIXTURE_GSPEED) // 60
        // Offset 64: motion heading
        buffer.putInt(FIXTURE_HEAD_MOT_RAW) // 64
        buffer.putInt(0) // 68 sAcc
        buffer.putInt(0) // 72 headAcc
        buffer.putShort(0) // 76 pDOP
        repeat(6) { buffer.put(0) } // 78-83 reserved
        // Offset 84: vehicle attitude
        buffer.putInt(FIXTURE_ROLL_RAW) // 84 vehRoll  (deg × 1e-5)
        buffer.putInt(FIXTURE_PITCH_RAW) // 88 vehPitch (deg × 1e-5)
        buffer.putInt(FIXTURE_HEAD_VEH_RAW) // 92 vehHeading (deg × 1e-5)
        buffer.putInt(0) // 96 motHeading (already at 64 for PVT layout — variant uses different slot)
        buffer.putInt(0) // 100 accRoll
        buffer.putInt(0) // 104 accPitch
        buffer.putInt(0) // 108 accHeading
        buffer.putInt(0) // 112 reserved
        return buffer.array()
    }
}
