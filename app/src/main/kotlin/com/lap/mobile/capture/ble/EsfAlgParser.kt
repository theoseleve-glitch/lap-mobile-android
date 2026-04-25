package com.lap.mobile.capture.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val ANGLE_SCALE: Double = 1e-2
private const val U1_TO_INT_MASK: Int = 0xFF
private const val U4_TO_LONG_MASK: Long = 0xFFFF_FFFFL

private const val OFFSET_ITOW: Int = 0
private const val OFFSET_VERSION: Int = 4
private const val OFFSET_FLAGS: Int = 5
private const val OFFSET_ERROR: Int = 6
private const val OFFSET_YAW: Int = 8
private const val OFFSET_PITCH: Int = 12
private const val OFFSET_ROLL: Int = 14

/**
 * Decodes UBX-ESF-ALG (T112). 16-byte payload — class/message
 * 0x10/0x14. Reports the device's auto-detected mount alignment
 * relative to the vehicle body frame; angles are pre-scaled to
 * degrees (raw × 1e-2). Yaw is unsigned [0, 360), pitch / roll
 * are signed.
 */
object EsfAlgParser {
    const val PAYLOAD_SIZE: Int = 16
    const val CLASS_ID: Int = 0x10
    const val MESSAGE_ID: Int = 0x14

    fun parse(payload: ByteArray): EsfAlg? {
        if (payload.size != PAYLOAD_SIZE) return null
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        return EsfAlg(
            iTOW = buffer.getInt(OFFSET_ITOW).toLong() and U4_TO_LONG_MASK,
            version = buffer.get(OFFSET_VERSION).toInt() and U1_TO_INT_MASK,
            flags = buffer.get(OFFSET_FLAGS).toInt() and U1_TO_INT_MASK,
            error = buffer.get(OFFSET_ERROR).toInt() and U1_TO_INT_MASK,
            yawDeg = (buffer.getInt(OFFSET_YAW).toLong() and U4_TO_LONG_MASK) * ANGLE_SCALE,
            pitchDeg = buffer.getShort(OFFSET_PITCH).toInt() * ANGLE_SCALE,
            rollDeg = buffer.getShort(OFFSET_ROLL).toInt() * ANGLE_SCALE,
        )
    }
}
