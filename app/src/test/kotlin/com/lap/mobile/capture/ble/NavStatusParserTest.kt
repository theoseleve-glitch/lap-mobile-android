package com.lap.mobile.capture.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val FIXTURE_ITOW: Long = 100_000L
private const val FIXTURE_GPS_FIX: Int = 3
private const val FIXTURE_FLAGS: Int = 0xC1
private const val FIXTURE_FIX_STAT: Int = 0x40
private const val FIXTURE_FLAGS2: Int = 0x10
private const val FIXTURE_TTFF: Long = 5_500L
private const val FIXTURE_MSSS: Long = 12_000L

class NavStatusParserTest {
    @Test
    fun parses_full_navstatus_fixture() {
        val payload = buildPayload()

        val parsed = requireNotNull(NavStatusParser.parse(payload))

        assertEquals(FIXTURE_ITOW, parsed.iTOW)
        assertEquals(FIXTURE_GPS_FIX, parsed.gpsFix)
        assertEquals(FIXTURE_FLAGS, parsed.flags)
        assertEquals(FIXTURE_FIX_STAT, parsed.fixStat)
        assertEquals(FIXTURE_FLAGS2, parsed.flags2)
        assertEquals(FIXTURE_TTFF, parsed.timeToFirstFixMs)
        assertEquals(FIXTURE_MSSS, parsed.millisSinceStartup)
    }

    @Test
    fun returns_null_for_wrong_size() {
        assertNull(NavStatusParser.parse(ByteArray(NavStatusParser.PAYLOAD_SIZE - 1)))
        assertNull(NavStatusParser.parse(ByteArray(NavStatusParser.PAYLOAD_SIZE + 1)))
        assertNull(NavStatusParser.parse(ByteArray(0)))
    }

    private fun buildPayload(): ByteArray {
        val buffer = ByteBuffer.allocate(NavStatusParser.PAYLOAD_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(FIXTURE_ITOW.toInt())
        buffer.put(FIXTURE_GPS_FIX.toByte())
        buffer.put(FIXTURE_FLAGS.toByte())
        buffer.put(FIXTURE_FIX_STAT.toByte())
        buffer.put(FIXTURE_FLAGS2.toByte())
        buffer.putInt(FIXTURE_TTFF.toInt())
        buffer.putInt(FIXTURE_MSSS.toInt())
        return buffer.array()
    }
}
