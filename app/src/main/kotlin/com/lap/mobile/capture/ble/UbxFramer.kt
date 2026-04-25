package com.lap.mobile.capture.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Reassembles UBX wire frames from a streaming `Flow<ByteArray>`
 * (T106). One UBX frame is:
 *
 * ```
 * +----+----+-----+----+--------+--------+----------+------+------+
 * | B5 | 62 | cls | id | len_lo | len_hi | payload  | ck_a | ck_b |
 * +----+----+-----+----+--------+--------+----------+------+------+
 *  sync(2)   header(4)              payload(N)        checksum(2)
 * ```
 *
 * `len` is little-endian payload length; checksum is the u-blox
 * Fletcher-style sum over `cls + id + len + payload` (sync bytes
 * NOT included).
 *
 * Streaming realities the framer handles:
 *  - Frames split across BLE notification payloads (T105 chunks
 *    are MTU-bounded; UBX frames can exceed an MTU).
 *  - Multiple frames packed into one chunk.
 *  - Garbage prefix bytes before the first sync (clock-start of
 *    the radio link can deliver tail of a previous frame).
 *  - Corrupt frames (bad checksum) — drop and resync to the next
 *    `B5 62`. A corrupt frame must NOT stall the stream.
 *
 * The internal buffer is a [MutableList]<Byte> (not ByteArray) for
 * cheap front-popping; throughput is ≤ ~50 Hz × ~100 bytes so any
 * extra allocation cost vs. an arraylist-backed ring buffer is
 * irrelevant compared to the I/O cost of the BLE stack itself.
 */
class UbxFramer {
    fun frames(bytes: Flow<ByteArray>): Flow<UbxFrame> = flow {
        val buffer = mutableListOf<Byte>()
        bytes.collect { chunk ->
            for (b in chunk) buffer.add(b)
            while (true) {
                val frame = tryExtractFrame(buffer) ?: break
                emit(frame)
            }
        }
    }

    /**
     * Pulls one frame off the front of [buffer] if a complete +
     * checksum-valid frame is available. Returns `null` when the
     * buffer needs more bytes. Per [Step], a corrupted frame
     * drops the leading sync byte and the next iteration retries.
     */
    private fun tryExtractFrame(buffer: MutableList<Byte>): UbxFrame? {
        var extracted: UbxFrame? = null
        var keepGoing = true
        while (keepGoing && extracted == null && buffer.size >= MIN_FRAME_BYTES) {
            when (val step = stepOnce(buffer)) {
                is Step.Frame -> extracted = step.frame
                Step.NeedMore -> keepGoing = false
                Step.SkipBadFrame -> Unit
            }
        }
        return extracted
    }

    private sealed interface Step {
        data class Frame(val frame: UbxFrame) : Step
        data object NeedMore : Step
        data object SkipBadFrame : Step
    }

    private fun stepOnce(buffer: MutableList<Byte>): Step {
        val syncIndex = findSync(buffer)
        if (syncIndex < 0) {
            // No sync at all — drop everything except possibly trailing 0xB5.
            val keepTail = if (buffer.isNotEmpty() && buffer.last() == SYNC_BYTE_1) 1 else 0
            val drop = buffer.size - keepTail
            repeat(drop) { buffer.removeAt(0) }
            return Step.NeedMore
        }
        repeat(syncIndex) { buffer.removeAt(0) }
        if (buffer.size < MIN_FRAME_BYTES) return Step.NeedMore

        val payloadLen = readPayloadLength(buffer)
        if (buffer.size < MIN_FRAME_BYTES + payloadLen) return Step.NeedMore

        return if (checksumValid(buffer, payloadLen)) {
            Step.Frame(popFrame(buffer, payloadLen))
        } else {
            buffer.removeAt(0)
            Step.SkipBadFrame
        }
    }

    private fun readPayloadLength(buffer: MutableList<Byte>): Int {
        val lenLo = buffer[LEN_LO_OFFSET].toInt() and BYTE_MASK
        val lenHi = buffer[LEN_HI_OFFSET].toInt() and BYTE_MASK
        return lenLo or (lenHi shl BYTE_BIT_WIDTH)
    }

    private fun checksumValid(buffer: MutableList<Byte>, payloadLen: Int): Boolean {
        var ckA = 0
        var ckB = 0
        for (i in CLASS_OFFSET until PAYLOAD_OFFSET + payloadLen) {
            ckA = (ckA + (buffer[i].toInt() and BYTE_MASK)) and BYTE_MASK
            ckB = (ckB + ckA) and BYTE_MASK
        }
        val expectedCkA = buffer[PAYLOAD_OFFSET + payloadLen].toInt() and BYTE_MASK
        val expectedCkB = buffer[PAYLOAD_OFFSET + payloadLen + 1].toInt() and BYTE_MASK
        return ckA == expectedCkA && ckB == expectedCkB
    }

    private fun popFrame(buffer: MutableList<Byte>, payloadLen: Int): UbxFrame {
        val classId = buffer[CLASS_OFFSET].toInt() and BYTE_MASK
        val messageId = buffer[MSG_ID_OFFSET].toInt() and BYTE_MASK
        val payload = ByteArray(payloadLen)
        for (i in 0 until payloadLen) payload[i] = buffer[PAYLOAD_OFFSET + i]
        val totalSize = MIN_FRAME_BYTES + payloadLen
        repeat(totalSize) { buffer.removeAt(0) }
        return UbxFrame(classId = classId, messageId = messageId, payload = payload)
    }

    private fun findSync(buffer: MutableList<Byte>): Int {
        for (i in 0 until buffer.size - 1) {
            if (buffer[i] == SYNC_BYTE_1 && buffer[i + 1] == SYNC_BYTE_2) return i
        }
        return -1
    }

    companion object {
        const val SYNC_BYTE_1: Byte = 0xB5.toByte()
        const val SYNC_BYTE_2: Byte = 0x62.toByte()

        // sync(2) + class(1) + id(1) + length(2) + checksum(2) = 8 bytes minimum
        const val MIN_FRAME_BYTES: Int = 8

        // Header offsets within a buffer that begins at the leading sync byte.
        private const val CLASS_OFFSET: Int = 2
        private const val MSG_ID_OFFSET: Int = 3
        private const val LEN_LO_OFFSET: Int = 4
        private const val LEN_HI_OFFSET: Int = 5
        private const val PAYLOAD_OFFSET: Int = 6

        private const val BYTE_MASK: Int = 0xFF
        private const val BYTE_BIT_WIDTH: Int = 8
    }
}
