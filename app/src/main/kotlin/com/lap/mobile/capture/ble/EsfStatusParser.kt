package com.lap.mobile.capture.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val U1_TO_INT_MASK: Int = 0xFF
private const val U4_TO_LONG_MASK: Long = 0xFFFF_FFFFL

private const val OFFSET_ITOW: Int = 0
private const val OFFSET_VERSION: Int = 4
private const val OFFSET_FUSION_MODE: Int = 12

/**
 * Decodes UBX-ESF-STATUS (T114). Variable-length payload —
 * 16-byte header (iTOW + version + reserved + fusionMode + reserved
 * + numSens) followed by N × 4-byte per-sensor records.
 *
 * Returns `null` for sizes that aren't header + N×4 — defensive
 * against length-vs-class drift. Per-sensor sub-fields stay raw
 * because the bit-field decode varies by sensor type and consumer
 * (UI vs forensic audit decode different bits).
 */
object EsfStatusParser {
    const val HEADER_SIZE: Int = 16
    const val SENSOR_RECORD_SIZE: Int = 4
    const val CLASS_ID: Int = 0x10
    const val MESSAGE_ID: Int = 0x10

    fun parse(payload: ByteArray): EsfStatus? {
        if (payload.size < HEADER_SIZE) return null
        val tail = payload.size - HEADER_SIZE
        if (tail % SENSOR_RECORD_SIZE != 0) return null

        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val iTOW = buffer.getInt(OFFSET_ITOW).toLong() and U4_TO_LONG_MASK
        val version = buffer.get(OFFSET_VERSION).toInt() and U1_TO_INT_MASK
        val fusionMode = buffer.get(OFFSET_FUSION_MODE).toInt() and U1_TO_INT_MASK

        buffer.position(HEADER_SIZE)
        val count = tail / SENSOR_RECORD_SIZE
        val sensors = ArrayList<EsfSensorStatus>(count)
        repeat(count) {
            sensors.add(
                EsfSensorStatus(
                    sensStatus1 = buffer.get().toInt() and U1_TO_INT_MASK,
                    sensStatus2 = buffer.get().toInt() and U1_TO_INT_MASK,
                    frequencyHz = buffer.get().toInt() and U1_TO_INT_MASK,
                    faults = buffer.get().toInt() and U1_TO_INT_MASK,
                ),
            )
        }
        return EsfStatus(iTOW = iTOW, version = version, fusionMode = fusionMode, sensors = sensors)
    }
}
