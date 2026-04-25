package com.lap.mobile.capture.ble

/**
 * Single UBX frame extracted by the [UbxFramer] (T106). All
 * [classId] / [messageId] values are unsigned ints encoded as
 * `Int` so callers can `when` against literals like `0x01` (NAV)
 * without `.toByte()` casts. [payload] is just the variable-length
 * body — no sync, header, or checksum bytes; the framer has
 * already stripped + validated those.
 */
data class UbxFrame(
    val classId: Int,
    val messageId: Int,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UbxFrame) return false
        return classId == other.classId &&
            messageId == other.messageId &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = classId
        result = 31 * result + messageId
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
