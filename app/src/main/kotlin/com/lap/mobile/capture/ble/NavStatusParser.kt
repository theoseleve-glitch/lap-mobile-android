package com.lap.mobile.capture.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val U4_TO_LONG_MASK: Long = 0xFFFF_FFFFL
private const val U1_TO_INT_MASK: Int = 0xFF

private const val OFFSET_ITOW: Int = 0
private const val OFFSET_GPS_FIX: Int = 4
private const val OFFSET_FLAGS: Int = 5
private const val OFFSET_FIX_STAT: Int = 6
private const val OFFSET_FLAGS2: Int = 7
private const val OFFSET_TTFF: Int = 8
private const val OFFSET_MSSS: Int = 12

/**
 * Decodes UBX-NAV-STATUS (T108). 16-byte payload — class/message
 * 0x01/0x03. Same defensive size guard as [NavPvtParser]: wrong
 * length returns `null` rather than producing a half-decoded
 * value.
 */
object NavStatusParser {
    const val PAYLOAD_SIZE: Int = 16
    const val CLASS_ID: Int = 0x01
    const val MESSAGE_ID: Int = 0x03

    fun parse(payload: ByteArray): NavStatus? {
        if (payload.size != PAYLOAD_SIZE) return null
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        return NavStatus(
            iTOW = buffer.getInt(OFFSET_ITOW).toLong() and U4_TO_LONG_MASK,
            gpsFix = buffer.get(OFFSET_GPS_FIX).toInt() and U1_TO_INT_MASK,
            flags = buffer.get(OFFSET_FLAGS).toInt() and U1_TO_INT_MASK,
            fixStat = buffer.get(OFFSET_FIX_STAT).toInt() and U1_TO_INT_MASK,
            flags2 = buffer.get(OFFSET_FLAGS2).toInt() and U1_TO_INT_MASK,
            timeToFirstFixMs = buffer.getInt(OFFSET_TTFF).toLong() and U4_TO_LONG_MASK,
            millisSinceStartup = buffer.getInt(OFFSET_MSSS).toLong() and U4_TO_LONG_MASK,
        )
    }
}
