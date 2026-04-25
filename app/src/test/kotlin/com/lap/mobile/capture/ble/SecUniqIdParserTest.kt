package com.lap.mobile.capture.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val FIXTURE_VERSION: Int = 1

/**
 * Unit tests for [SecUniqIdParser] (T115). The on-the-wire bytes
 * are little-endian relative to nibble 0; the canonicalised hex
 * surfaces them in spec-canonical big-endian order
 * (most-significant-byte first) which is what the bundle
 * `provenance.device_id` field stores per ADR-012.
 */
class SecUniqIdParserTest {
    @Test
    fun parses_unique_id_and_canonicalises_to_lowercase_hex() {
        // Layout: version(1) + reserved(3) + uniqueId(5)
        val payload = byteArrayOf(
            FIXTURE_VERSION.toByte(),
            0, 0, 0,
            0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte(), 0x12, 0x34,
        )

        val parsed = requireNotNull(SecUniqIdParser.parse(payload))

        assertEquals(FIXTURE_VERSION, parsed.version)
        assertEquals("abcdef1234", parsed.deviceIdHex)
    }

    @Test
    fun pads_high_zero_bytes_for_short_id() {
        val payload = byteArrayOf(
            FIXTURE_VERSION.toByte(),
            0, 0, 0,
            0x01, 0x00, 0x00, 0x00, 0x00,
        )

        val parsed = requireNotNull(SecUniqIdParser.parse(payload))

        assertEquals(
            "trailing zero bytes preserved — fixed-width hex",
            "0100000000",
            parsed.deviceIdHex,
        )
    }

    @Test
    fun returns_null_for_wrong_size() {
        assertNull(SecUniqIdParser.parse(ByteArray(SecUniqIdParser.PAYLOAD_SIZE - 1)))
        assertNull(SecUniqIdParser.parse(ByteArray(SecUniqIdParser.PAYLOAD_SIZE + 1)))
        assertNull(SecUniqIdParser.parse(ByteArray(0)))
    }
}
