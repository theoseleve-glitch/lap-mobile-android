package com.lap.mobile.capture.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val FIXTURE_SW_VERSION: String = "EXT CORE 1.00 (a1b2c3)"
private const val FIXTURE_HW_VERSION: String = "00080000"
private const val FIXTURE_EXT_PROTVER: String = "PROTVER=27.50"
private const val FIXTURE_EXT_MOD: String = "MOD=ZED-F9P"
private const val EXPECTED_TWO_EXTENSIONS: Int = 2

class MonVerParserTest {
    @Test
    fun parses_software_and_hardware_version_with_zero_extensions() {
        val payload = buildPayload(FIXTURE_SW_VERSION, FIXTURE_HW_VERSION, emptyList())

        val parsed = requireNotNull(MonVerParser.parse(payload))

        assertEquals(FIXTURE_SW_VERSION, parsed.softwareVersion)
        assertEquals(FIXTURE_HW_VERSION, parsed.hardwareVersion)
        assertTrue(parsed.extensions.isEmpty())
    }

    @Test
    fun parses_extensions_in_order() {
        val payload = buildPayload(
            FIXTURE_SW_VERSION,
            FIXTURE_HW_VERSION,
            listOf(FIXTURE_EXT_PROTVER, FIXTURE_EXT_MOD),
        )

        val parsed = requireNotNull(MonVerParser.parse(payload))

        assertEquals(EXPECTED_TWO_EXTENSIONS, parsed.extensions.size)
        assertEquals(FIXTURE_EXT_PROTVER, parsed.extensions[0])
        assertEquals(FIXTURE_EXT_MOD, parsed.extensions[1])
    }

    @Test
    fun trims_at_first_null_byte() {
        // Pad sw version manually so non-zero garbage past the null terminator
        // is correctly truncated.
        val raw = ByteArray(MonVerParser.HEADER_SIZE)
        val sw = "1.0.0".toByteArray(Charsets.US_ASCII)
        sw.copyInto(raw, 0)
        raw[sw.size] = 0
        // garbage bytes after the null terminator
        for (i in (sw.size + 1) until MonVerParser.SW_VERSION_SIZE) {
            raw[i] = 0xFF.toByte()
        }
        val hw = "garbage".toByteArray(Charsets.US_ASCII)
        hw.copyInto(raw, MonVerParser.SW_VERSION_SIZE)
        raw[MonVerParser.SW_VERSION_SIZE + hw.size] = 0

        val parsed = requireNotNull(MonVerParser.parse(raw))

        assertEquals("1.0.0", parsed.softwareVersion)
        assertEquals("garbage", parsed.hardwareVersion)
    }

    @Test
    fun returns_null_when_payload_too_short_for_header() {
        assertNull(MonVerParser.parse(ByteArray(MonVerParser.HEADER_SIZE - 1)))
    }

    @Test
    fun returns_null_when_extension_tail_size_not_multiple_of_30() {
        // header + 5 bytes (not multiple of 30)
        assertNull(MonVerParser.parse(ByteArray(MonVerParser.HEADER_SIZE + 5)))
    }

    private fun buildPayload(swVersion: String, hwVersion: String, extensions: List<String>): ByteArray {
        val size = MonVerParser.HEADER_SIZE + extensions.size * MonVerParser.EXTENSION_SIZE
        val out = ByteArray(size)
        writeFixedField(out, 0, swVersion, MonVerParser.SW_VERSION_SIZE)
        writeFixedField(out, MonVerParser.SW_VERSION_SIZE, hwVersion, MonVerParser.HW_VERSION_SIZE)
        for ((index, ext) in extensions.withIndex()) {
            val offset = MonVerParser.HEADER_SIZE + index * MonVerParser.EXTENSION_SIZE
            writeFixedField(out, offset, ext, MonVerParser.EXTENSION_SIZE)
        }
        return out
    }

    private fun writeFixedField(out: ByteArray, offset: Int, value: String, fieldSize: Int) {
        val bytes = value.toByteArray(Charsets.US_ASCII)
        require(bytes.size < fieldSize) { "value '$value' must leave room for null terminator" }
        bytes.copyInto(out, offset)
        // Remaining bytes default to 0 — null-padded.
    }
}
