package com.lap.mobile.capture.ble

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NAV_CLASS: Int = 0x01
private const val MSG_PVT: Int = 0x07
private const val MSG_STATUS: Int = 0x03

/**
 * Unit tests for [UbxFramer] (T106). Covers the full UBX wire
 * contract — sync-byte detection, little-endian length, Fletcher
 * checksum validation — plus the realities of a streaming source
 * where frames cross chunk boundaries, multiple frames pack into
 * one chunk, and corrupt frames need to be silently dropped without
 * stalling the stream.
 */
class UbxFramerTest {
    @Test
    fun emits_single_complete_frame_in_single_chunk() = runTest {
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val frame = encodeFrame(NAV_CLASS, MSG_PVT, payload)

        val framer = UbxFramer()
        val collected = framer.frames(flowOf(frame)).toList()

        assertEquals(1, collected.size)
        assertEquals(NAV_CLASS, collected[0].classId)
        assertEquals(MSG_PVT, collected[0].messageId)
        assertTrue(payload.contentEquals(collected[0].payload))
    }

    @Test
    fun reassembles_frame_split_across_two_chunks() = runTest {
        val payload = byteArrayOf(0xA1.toByte(), 0xB2.toByte(), 0xC3.toByte())
        val frame = encodeFrame(NAV_CLASS, MSG_STATUS, payload)
        val mid = frame.size / 2
        val chunkA = frame.copyOfRange(0, mid)
        val chunkB = frame.copyOfRange(mid, frame.size)

        val framer = UbxFramer()
        val collected = framer.frames(flowOf(chunkA, chunkB)).toList()

        assertEquals(1, collected.size)
        assertEquals(NAV_CLASS, collected[0].classId)
        assertEquals(MSG_STATUS, collected[0].messageId)
        assertTrue(payload.contentEquals(collected[0].payload))
    }

    @Test
    fun emits_two_complete_frames_in_one_chunk() = runTest {
        val payloadA = byteArrayOf(0x01, 0x02)
        val payloadB = byteArrayOf(0x10, 0x20, 0x30)
        val frameA = encodeFrame(NAV_CLASS, MSG_PVT, payloadA)
        val frameB = encodeFrame(NAV_CLASS, MSG_STATUS, payloadB)
        val combined = frameA + frameB

        val framer = UbxFramer()
        val collected = framer.frames(flowOf(combined)).toList()

        assertEquals(2, collected.size)
        assertEquals(MSG_PVT, collected[0].messageId)
        assertEquals(MSG_STATUS, collected[1].messageId)
    }

    @Test
    fun skips_garbage_bytes_before_sync() = runTest {
        val garbage = byteArrayOf(0x00, 0xFF.toByte(), 0xAB.toByte(), 0xCD.toByte())
        val payload = byteArrayOf(0x42)
        val frame = encodeFrame(NAV_CLASS, MSG_PVT, payload)
        val withGarbage = garbage + frame

        val framer = UbxFramer()
        val collected = framer.frames(flowOf(withGarbage)).toList()

        assertEquals(1, collected.size)
        assertEquals(MSG_PVT, collected[0].messageId)
    }

    @Test
    fun drops_frame_with_bad_checksum_and_recovers_to_next_frame() = runTest {
        val goodPayload = byteArrayOf(0x55)
        val badFrame = encodeFrame(NAV_CLASS, MSG_PVT, goodPayload).also { frame ->
            // Corrupt the last byte of checksum.
            frame[frame.size - 1] = (frame[frame.size - 1].toInt() xor 0xFF).toByte()
        }
        val goodFrame = encodeFrame(NAV_CLASS, MSG_STATUS, byteArrayOf(0x66))

        val framer = UbxFramer()
        val collected = framer.frames(flowOf(badFrame + goodFrame)).toList()

        // Bad frame is dropped, good frame is emitted.
        assertEquals(1, collected.size)
        assertEquals(MSG_STATUS, collected[0].messageId)
    }

    @Test
    fun handles_empty_payload_frame() = runTest {
        val frame = encodeFrame(NAV_CLASS, MSG_PVT, ByteArray(0))

        val framer = UbxFramer()
        val collected = framer.frames(flowOf(frame)).toList()

        assertEquals(1, collected.size)
        assertEquals(0, collected[0].payload.size)
    }

    @Test
    fun no_complete_frame_yet_emits_nothing_then_completes() = runTest {
        // Just a partial header — never enough to extract a frame.
        val partial = byteArrayOf(0xB5.toByte(), 0x62.toByte(), 0x01)

        val framer = UbxFramer()
        val collected = framer.frames(flowOf(partial)).toList()

        assertTrue("partial frame must not emit", collected.isEmpty())
    }

    /**
     * Build a valid UBX frame: sync(2) + class(1) + id(1) + len(2 LE)
     * + payload + ck_a + ck_b. Checksum is Fletcher-style over
     * everything except the sync bytes — same as u-blox spec.
     */
    private fun encodeFrame(classId: Int, messageId: Int, payload: ByteArray): ByteArray {
        val length = payload.size
        val header = byteArrayOf(
            classId.toByte(),
            messageId.toByte(),
            (length and 0xFF).toByte(),
            ((length shr 8) and 0xFF).toByte(),
        )
        val body = header + payload
        var ckA = 0
        var ckB = 0
        for (b in body) {
            ckA = (ckA + (b.toInt() and 0xFF)) and 0xFF
            ckB = (ckB + ckA) and 0xFF
        }
        return byteArrayOf(0xB5.toByte(), 0x62.toByte()) + body + byteArrayOf(ckA.toByte(), ckB.toByte())
    }
}
