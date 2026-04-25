package com.lap.mobile.capture.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val U2_TO_INT_MASK: Int = 0xFFFF
private const val U4_TO_LONG_MASK: Long = 0xFFFF_FFFFL
private const val DATA_VALUE_MASK_24: Int = 0x00FF_FFFF
private const val DATA_TYPE_SHIFT: Int = 24
private const val DATA_TYPE_MASK: Int = 0xFF
private const val SIGN_BIT_24: Int = 0x0080_0000
private const val SIGN_EXTEND_HIGH: Int = 0xFF00_0000.toInt()

private const val OFFSET_TIME_TAG: Int = 0
private const val OFFSET_FLAGS: Int = 4
private const val OFFSET_ID: Int = 6
private const val FLAGS_CALIB_TTAG_VALID_BIT: Int = 0x0008

/**
 * Decodes UBX-ESF-MEAS (T113). Variable-length payload —
 * 8-byte header (timeTag + flags + id) followed by N × 4-byte
 * measurement records and an optional 4-byte `calibTtag` trailer.
 *
 * The measurement count is *derived from payload length* rather
 * than the flags-bits-3-6 numMeas field — length is the more
 * trustworthy source because firmware quirks have been reported
 * where flags don't agree with the actual record count. The
 * calibTtag is recognised by *exactly* one extra trailing word
 * after the measurement region — same length-based heuristic.
 *
 * Returns `null` for sizes that aren't header + N×4 + {0|4} —
 * defensive against length-vs-class drift.
 */
object EsfMeasParser {
    const val HEADER_SIZE: Int = 8
    const val MEASUREMENT_SIZE: Int = 4
    const val CALIB_TTAG_SIZE: Int = 4
    const val CLASS_ID: Int = 0x10
    const val MESSAGE_ID: Int = 0x02

    fun parse(payload: ByteArray): EsfMeas? {
        if (payload.size < HEADER_SIZE) return null

        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val timeTag = buffer.getInt(OFFSET_TIME_TAG).toLong() and U4_TO_LONG_MASK
        val flags = buffer.getShort(OFFSET_FLAGS).toInt() and U2_TO_INT_MASK
        val id = buffer.getShort(OFFSET_ID).toInt() and U2_TO_INT_MASK

        val hasCalib = (flags and FLAGS_CALIB_TTAG_VALID_BIT) != 0
        val tail = payload.size - HEADER_SIZE - if (hasCalib) CALIB_TTAG_SIZE else 0
        if (tail < 0 || tail % MEASUREMENT_SIZE != 0) return null

        buffer.position(HEADER_SIZE)
        val count = tail / MEASUREMENT_SIZE
        val measurements = ArrayList<EsfMeasMeasurement>(count)
        repeat(count) {
            val packed = buffer.getInt()
            val dataType = (packed ushr DATA_TYPE_SHIFT) and DATA_TYPE_MASK
            val rawValue = packed and DATA_VALUE_MASK_24
            val signed = if ((rawValue and SIGN_BIT_24) != 0) rawValue or SIGN_EXTEND_HIGH else rawValue
            measurements.add(EsfMeasMeasurement(dataType = dataType, dataValue = signed))
        }
        val calibTtag = if (hasCalib) buffer.getInt().toLong() and U4_TO_LONG_MASK else null
        return EsfMeas(
            timeTag = timeTag,
            flags = flags,
            id = id,
            measurements = measurements,
            calibTimeTag = calibTtag,
        )
    }
}
