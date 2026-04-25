package com.lap.mobile.capture.ble

private const val U1_TO_INT_MASK: Int = 0xFF
private const val OFFSET_VERSION: Int = 0
private const val OFFSET_UNIQUE_ID: Int = 4

/**
 * Decodes UBX-SEC-UNIQID (T115). 9-byte payload — 1-byte version
 * + 3 reserved bytes + 5-byte chip-unique ID.
 *
 * The 5-byte ID is rendered as a fixed-width 10-character lowercase
 * hex string (no `0x` prefix) so the value round-trips into the
 * bundle's `provenance.device_id` field per ADR-012 ingestion
 * shape. Trailing zero bytes are preserved (not trimmed) — the
 * value is an opaque identifier, not a number.
 */
object SecUniqIdParser {
    const val PAYLOAD_SIZE: Int = 9
    const val UNIQUE_ID_SIZE: Int = 5
    const val CLASS_ID: Int = 0x27
    const val MESSAGE_ID: Int = 0x03

    fun parse(payload: ByteArray): SecUniqId? {
        if (payload.size != PAYLOAD_SIZE) return null
        val version = payload[OFFSET_VERSION].toInt() and U1_TO_INT_MASK
        val hex = buildString(UNIQUE_ID_SIZE * 2) {
            for (i in 0 until UNIQUE_ID_SIZE) {
                val byte = payload[OFFSET_UNIQUE_ID + i].toInt() and U1_TO_INT_MASK
                append(byte.toString(radix = 16).padStart(2, '0'))
            }
        }
        return SecUniqId(version = version, deviceIdHex = hex)
    }
}
