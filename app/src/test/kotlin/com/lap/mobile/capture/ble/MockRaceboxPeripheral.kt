package com.lap.mobile.capture.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * JVM-side mock RaceBox peripheral (T129). Emits a deterministic
 * [Flow] of well-formed UBX byte chunks the way a real BLE
 * notification subscription would — sized so frame boundaries fall
 * inside chunks, exercising the [UbxFramer] cross-chunk reassembly
 * path.
 *
 * The full Android-emulator + mock-peripheral integration test
 * (binding through Nordic's BleManager + a fake GATT server) lives
 * `[PENDING: device-required]`; this fixture is what that test would
 * use as its byte source, and is exercised here by JVM tests that
 * round-trip the bytes through [UbxFramer] and the per-class parsers.
 */
object MockRaceboxPeripheral {
    private const val SYNC1: Byte = 0xB5.toByte()
    private const val SYNC2: Byte = 0x62.toByte()
    private const val UBX_NAV_CLASS: Int = 0x01
    private const val UBX_NAV_PVT_ID: Int = 0x07
    private const val FRAME_OVERHEAD_BYTES: Int = 8
    private const val PAYLOAD_OFFSET_IN_FRAME: Int = 6
    private const val BYTE_MASK: Int = 0xFF
    private const val DEFAULT_BLE_MTU_PAYLOAD: Int = 23

    private const val YEAR_2026: Short = 2026
    private const val MONTH_APRIL: Byte = 4
    private const val DAY_25: Byte = 25
    private const val HOUR_14: Byte = 14
    private const val MIN_30: Byte = 30
    private const val SEC_12: Byte = 12

    /**
     * Emit [frameCount] copies of a canonical NAV-PVT frame as a
     * single [Flow]. Each chunk carries [chunkBytes] bytes, defaulting
     * to a 23-byte BLE MTU-sized chunk so the reassembly path gets
     * exercised the same way it would on a real BLE link.
     */
    fun navPvtFlow(frameCount: Int, chunkBytes: Int = DEFAULT_BLE_MTU_PAYLOAD): Flow<ByteArray> {
        val singleFrame = buildOneNavPvtFrame()
        val totalSize = singleFrame.size * frameCount
        val stream = ByteArray(totalSize)
        for (i in 0 until frameCount) {
            singleFrame.copyInto(stream, destinationOffset = i * singleFrame.size)
        }
        return flowOf(*chunkify(stream, chunkBytes))
    }

    private fun chunkify(stream: ByteArray, chunkBytes: Int): Array<ByteArray> {
        val parts = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < stream.size) {
            val end = (offset + chunkBytes).coerceAtMost(stream.size)
            parts.add(stream.copyOfRange(offset, end))
            offset = end
        }
        return parts.toTypedArray()
    }

    private fun buildOneNavPvtFrame(): ByteArray {
        val frameSize = FRAME_OVERHEAD_BYTES + NavPvtParser.PAYLOAD_SIZE
        val frame = ByteArray(frameSize)
        frame[0] = SYNC1
        frame[1] = SYNC2
        frame[2] = UBX_NAV_CLASS.toByte()
        frame[3] = UBX_NAV_PVT_ID.toByte()
        frame[4] = (NavPvtParser.PAYLOAD_SIZE and BYTE_MASK).toByte()
        frame[5] = ((NavPvtParser.PAYLOAD_SIZE ushr Byte.SIZE_BITS) and BYTE_MASK).toByte()
        val buf = ByteBuffer.wrap(frame, PAYLOAD_OFFSET_IN_FRAME, NavPvtParser.PAYLOAD_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(0)
        buf.putShort(YEAR_2026)
        buf.put(MONTH_APRIL)
        buf.put(DAY_25)
        buf.put(HOUR_14)
        buf.put(MIN_30)
        buf.put(SEC_12)
        var ckA = 0
        var ckB = 0
        for (i in 2 until 2 + 4 + NavPvtParser.PAYLOAD_SIZE) {
            ckA = (ckA + (frame[i].toInt() and BYTE_MASK)) and BYTE_MASK
            ckB = (ckB + ckA) and BYTE_MASK
        }
        frame[frame.size - 2] = ckA.toByte()
        frame[frame.size - 1] = ckB.toByte()
        return frame
    }
}
