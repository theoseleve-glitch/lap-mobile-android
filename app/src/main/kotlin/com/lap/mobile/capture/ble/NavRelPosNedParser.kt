package com.lap.mobile.capture.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val HEAD_SCALE: Double = 1e-5
private const val U1_TO_INT_MASK: Int = 0xFF
private const val U2_TO_INT_MASK: Int = 0xFFFF
private const val U4_TO_LONG_MASK: Long = 0xFFFF_FFFFL

private const val OFFSET_VERSION: Int = 0
private const val OFFSET_REF_STATION: Int = 2
private const val OFFSET_ITOW: Int = 4
private const val OFFSET_REL_N: Int = 8
private const val OFFSET_REL_E: Int = 12
private const val OFFSET_REL_D: Int = 16
private const val OFFSET_REL_LENGTH: Int = 20
private const val OFFSET_REL_HEADING: Int = 24
private const val OFFSET_FLAGS: Int = 60

/**
 * Decodes UBX-NAV-RELPOSNED (T110). 64-byte version-1 payload —
 * class/message 0x01/0x3C. Used for RTK rover-to-base baseline
 * reporting; lap detection doesn't strictly need it but the parser
 * is around for a future RTK-precision coaching mode.
 *
 * Distance fields stay in cm (raw spec); heading pre-scaled to
 * degrees (raw is degrees × 1e-5). The high-precision tail
 * (relPosHP* + acc* + reserved3) is unused and intentionally
 * skipped — adding those fields later is one offset edit.
 */
object NavRelPosNedParser {
    const val PAYLOAD_SIZE: Int = 64
    const val CLASS_ID: Int = 0x01
    const val MESSAGE_ID: Int = 0x3C

    fun parse(payload: ByteArray): NavRelPosNed? {
        if (payload.size != PAYLOAD_SIZE) return null
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        return NavRelPosNed(
            version = buffer.get(OFFSET_VERSION).toInt() and U1_TO_INT_MASK,
            refStationId = buffer.getShort(OFFSET_REF_STATION).toInt() and U2_TO_INT_MASK,
            iTOW = buffer.getInt(OFFSET_ITOW).toLong() and U4_TO_LONG_MASK,
            relPosNorthCm = buffer.getInt(OFFSET_REL_N),
            relPosEastCm = buffer.getInt(OFFSET_REL_E),
            relPosDownCm = buffer.getInt(OFFSET_REL_D),
            relPosLengthCm = buffer.getInt(OFFSET_REL_LENGTH),
            relPosHeadingDeg = buffer.getInt(OFFSET_REL_HEADING) * HEAD_SCALE,
            flags = buffer.getInt(OFFSET_FLAGS).toLong() and U4_TO_LONG_MASK,
        )
    }
}
