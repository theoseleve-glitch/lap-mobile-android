package com.lap.mobile.capture.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val U4_TO_LONG_MASK: Long = 0xFFFF_FFFFL
private const val DATA_VALUE_MASK_24: Int = 0x00FF_FFFF
private const val DATA_TYPE_SHIFT: Int = 24
private const val DATA_TYPE_MASK: Int = 0xFF
private const val SIGN_BIT_24: Int = 0x0080_0000
private const val SIGN_EXTEND_HIGH: Int = 0xFF00_0000.toInt()

/**
 * Decodes UBX-ESF-RAW (T111). Variable-length payload —
 * 4-byte reserved header followed by N × 8-byte measurement
 * records. Each record packs a 24-bit signed `data` value plus an
 * 8-bit data type into a single 32-bit word, with a 32-bit sensor
 * time-tag tail.
 *
 * The 24-bit value is **sign-extended** to a signed `Int` because
 * accel / gyro readings can be negative — naive masking would
 * surface −1500 raw as 16,775,716. Mid-range positive values stay
 * untouched.
 *
 * Returns `null` for payloads smaller than the reserved header or
 * whose post-header length isn't an exact multiple of 8 bytes
 * (defensive: a length-vs-class drift would produce silently
 * truncated measurements otherwise).
 */
object EsfRawParser {
    const val HEADER_SIZE: Int = 4
    const val MEASUREMENT_SIZE: Int = 8
    const val CLASS_ID: Int = 0x10
    const val MESSAGE_ID: Int = 0x03

    fun parse(payload: ByteArray): EsfRaw? {
        if (payload.size < HEADER_SIZE) return null
        val measurementBytes = payload.size - HEADER_SIZE
        if (measurementBytes % MEASUREMENT_SIZE != 0) return null

        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(HEADER_SIZE)
        val count = measurementBytes / MEASUREMENT_SIZE
        val measurements = ArrayList<EsfRawMeasurement>(count)
        repeat(count) {
            val packed = buffer.getInt()
            val dataType = (packed ushr DATA_TYPE_SHIFT) and DATA_TYPE_MASK
            val rawValue = packed and DATA_VALUE_MASK_24
            val signed = if ((rawValue and SIGN_BIT_24) != 0) rawValue or SIGN_EXTEND_HIGH else rawValue
            val sTtag = buffer.getInt().toLong() and U4_TO_LONG_MASK
            measurements.add(
                EsfRawMeasurement(dataType = dataType, dataValue = signed, sensorTimeTag = sTtag),
            )
        }
        return EsfRaw(measurements = measurements)
    }
}
