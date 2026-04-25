package com.lap.mobile.capture.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val FIXTURE_ITOW: Long = 50_000L
private const val FIXTURE_VERSION: Int = 1
private const val FIXTURE_FLAGS: Int = 0x05
private const val FIXTURE_ERROR: Int = 0x00
private const val FIXTURE_YAW_RAW: Long = 35_500L
private const val FIXTURE_PITCH_RAW: Int = -250
private const val FIXTURE_ROLL_RAW: Int = 100
private const val EXPECTED_YAW_DEG: Double = 355.0
private const val EXPECTED_PITCH_DEG: Double = -2.5
private const val EXPECTED_ROLL_DEG: Double = 1.0
private const val FLOAT_TOLERANCE: Double = 1e-7

class EsfAlgParserTest {
    @Test
    fun parses_full_esfalg_fixture() {
        val payload = buildPayload()

        val parsed = requireNotNull(EsfAlgParser.parse(payload))

        assertEquals(FIXTURE_ITOW, parsed.iTOW)
        assertEquals(FIXTURE_VERSION, parsed.version)
        assertEquals(FIXTURE_FLAGS, parsed.flags)
        assertEquals(FIXTURE_ERROR, parsed.error)
        assertEquals(EXPECTED_YAW_DEG, parsed.yawDeg, FLOAT_TOLERANCE)
        assertEquals(EXPECTED_PITCH_DEG, parsed.pitchDeg, FLOAT_TOLERANCE)
        assertEquals(EXPECTED_ROLL_DEG, parsed.rollDeg, FLOAT_TOLERANCE)
    }

    @Test
    fun returns_null_for_wrong_size() {
        assertNull(EsfAlgParser.parse(ByteArray(EsfAlgParser.PAYLOAD_SIZE - 1)))
        assertNull(EsfAlgParser.parse(ByteArray(EsfAlgParser.PAYLOAD_SIZE + 1)))
        assertNull(EsfAlgParser.parse(ByteArray(0)))
    }

    private fun buildPayload(): ByteArray {
        val buffer = ByteBuffer.allocate(EsfAlgParser.PAYLOAD_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(FIXTURE_ITOW.toInt()) // 0  iTOW (U4)
        buffer.put(FIXTURE_VERSION.toByte()) // 4  version (U1)
        buffer.put(FIXTURE_FLAGS.toByte()) // 5  flags (X1)
        buffer.put(FIXTURE_ERROR.toByte()) // 6  error (X1)
        buffer.put(0) // 7  reserved
        buffer.putInt(FIXTURE_YAW_RAW.toInt()) // 8  yaw (U4 deg × 1e-2)
        buffer.putShort(FIXTURE_PITCH_RAW.toShort()) // 12 pitch (I2 deg × 1e-2)
        buffer.putShort(FIXTURE_ROLL_RAW.toShort()) // 14 roll (I2 deg × 1e-2)
        return buffer.array()
    }
}
