package com.lap.mobile.capture.ble

/**
 * Decodes UBX-MON-VER (T116). Variable-length payload —
 * 30-byte software-version + 10-byte hardware-version + N × 30-byte
 * extensions. Each field is a null-terminated ASCII string,
 * possibly null-padded; trim at the first null byte and decode
 * the prefix as US-ASCII so non-ASCII bytes (which the spec
 * forbids) don't accidentally surface as malformed UTF-8.
 *
 * Returns `null` when the post-header tail isn't a clean multiple
 * of 30 bytes — defensive against length-vs-class drift.
 */
object MonVerParser {
    const val SW_VERSION_SIZE: Int = 30
    const val HW_VERSION_SIZE: Int = 10
    const val EXTENSION_SIZE: Int = 30
    const val HEADER_SIZE: Int = SW_VERSION_SIZE + HW_VERSION_SIZE
    const val CLASS_ID: Int = 0x0A
    const val MESSAGE_ID: Int = 0x04

    fun parse(payload: ByteArray): MonVer? {
        if (payload.size < HEADER_SIZE) return null
        val tail = payload.size - HEADER_SIZE
        if (tail % EXTENSION_SIZE != 0) return null

        val sw = readNullTerminated(payload, 0, SW_VERSION_SIZE)
        val hw = readNullTerminated(payload, SW_VERSION_SIZE, HW_VERSION_SIZE)
        val extensionCount = tail / EXTENSION_SIZE
        val extensions = ArrayList<String>(extensionCount)
        for (i in 0 until extensionCount) {
            val offset = HEADER_SIZE + i * EXTENSION_SIZE
            extensions.add(readNullTerminated(payload, offset, EXTENSION_SIZE))
        }
        return MonVer(softwareVersion = sw, hardwareVersion = hw, extensions = extensions)
    }

    private fun readNullTerminated(bytes: ByteArray, offset: Int, fieldSize: Int): String {
        var end = offset
        val limit = offset + fieldSize
        while (end < limit && bytes[end].toInt() != 0) end++
        return String(bytes, offset, end - offset, Charsets.US_ASCII)
    }
}
