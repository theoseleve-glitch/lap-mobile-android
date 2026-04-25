package com.lap.mobile.capture.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val FIXTURE_VERSION: Int = 1
private const val FIXTURE_REF_STATION: Int = 4321
private const val FIXTURE_ITOW: Long = 300_000L
private const val FIXTURE_REL_N_CM: Int = 1_500
private const val FIXTURE_REL_E_CM: Int = -800
private const val FIXTURE_REL_D_CM: Int = 25
private const val FIXTURE_REL_LEN_CM: Int = 1_703
private const val FIXTURE_REL_HEAD_RAW: Int = 18_000_000
private const val FIXTURE_FLAGS: Long = 0x0000_003FL
private const val EXPECTED_HEAD_DEG: Double = 180.0
private const val FLOAT_TOLERANCE: Double = 1e-7

/**
 * Unit tests for [NavRelPosNedParser] (T110). Constructs a
 * 64-byte version-1 payload (high-precision tail bytes default to
 * zero — those fields aren't surfaced yet) and verifies the parser
 * extracts the baseline + heading + flags.
 */
class NavRelPosNedParserTest {
    @Test
    fun parses_full_relposned_fixture() {
        val payload = buildPayload()

        val parsed = requireNotNull(NavRelPosNedParser.parse(payload))

        assertEquals(FIXTURE_VERSION, parsed.version)
        assertEquals(FIXTURE_REF_STATION, parsed.refStationId)
        assertEquals(FIXTURE_ITOW, parsed.iTOW)
        assertEquals(FIXTURE_REL_N_CM, parsed.relPosNorthCm)
        assertEquals(FIXTURE_REL_E_CM, parsed.relPosEastCm)
        assertEquals(FIXTURE_REL_D_CM, parsed.relPosDownCm)
        assertEquals(FIXTURE_REL_LEN_CM, parsed.relPosLengthCm)
        assertEquals(EXPECTED_HEAD_DEG, parsed.relPosHeadingDeg, FLOAT_TOLERANCE)
        assertEquals(FIXTURE_FLAGS, parsed.flags)
    }

    @Test
    fun returns_null_for_wrong_size() {
        assertNull(NavRelPosNedParser.parse(ByteArray(NavRelPosNedParser.PAYLOAD_SIZE - 1)))
        assertNull(NavRelPosNedParser.parse(ByteArray(NavRelPosNedParser.PAYLOAD_SIZE + 1)))
        assertNull(NavRelPosNedParser.parse(ByteArray(0)))
    }

    @Suppress("LongMethod")
    private fun buildPayload(): ByteArray {
        val buffer = ByteBuffer.allocate(NavRelPosNedParser.PAYLOAD_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(FIXTURE_VERSION.toByte()) // 0  version (U1)
        buffer.put(0) // 1  reserved1
        buffer.putShort(FIXTURE_REF_STATION.toShort()) // 2  refStationId (U2)
        buffer.putInt(FIXTURE_ITOW.toInt()) // 4  iTOW (U4)
        buffer.putInt(FIXTURE_REL_N_CM) // 8  relPosN (I4 cm)
        buffer.putInt(FIXTURE_REL_E_CM) // 12 relPosE (I4 cm)
        buffer.putInt(FIXTURE_REL_D_CM) // 16 relPosD (I4 cm)
        buffer.putInt(FIXTURE_REL_LEN_CM) // 20 relPosLength (I4 cm)
        buffer.putInt(FIXTURE_REL_HEAD_RAW) // 24 relPosHeading (I4 deg × 1e-5)
        buffer.putInt(0) // 28 reserved2
        buffer.put(0) // 32 relPosHPN (I1)
        buffer.put(0) // 33 relPosHPE (I1)
        buffer.put(0) // 34 relPosHPD (I1)
        buffer.put(0) // 35 relPosHPLength (I1)
        buffer.putInt(0) // 36 accN (U4)
        buffer.putInt(0) // 40 accE (U4)
        buffer.putInt(0) // 44 accD (U4)
        buffer.putInt(0) // 48 accLength (U4)
        buffer.putInt(0) // 52 accHeading (U4)
        buffer.putInt(0) // 56 reserved3
        buffer.putInt(FIXTURE_FLAGS.toInt()) // 60 flags (X4)
        return buffer.array()
    }
}
