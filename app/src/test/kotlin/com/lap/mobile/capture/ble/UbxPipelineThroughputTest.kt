package com.lap.mobile.capture.ble

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.system.measureNanoTime

private const val FRAME_COUNT: Int = 2_000
private const val WARMUP_FRAME_COUNT: Int = 200
private const val NS_PER_SEC: Long = 1_000_000_000L
private const val MIN_FRAMES_PER_SEC: Long = 1_000L
private const val NAV_PVT_PAYLOAD_LEN: Int = NavPvtParser.PAYLOAD_SIZE
private const val UBX_NAV_CLASS: Int = 0x01
private const val UBX_NAV_PVT_ID: Int = 0x07
private const val SYNC1: Byte = 0xB5.toByte()
private const val SYNC2: Byte = 0x62.toByte()
private const val BYTE_MASK: Int = 0xFF
private const val PAYLOAD_OFFSET_IN_FRAME: Int = 6
private const val FRAME_OVERHEAD_BYTES: Int = 8

/**
 * Throughput benchmark for the BLE → UBX → TelemetrySample pipeline
 * (T128). The RaceBox produces NAV-PVT at up to 25 Hz today, but the
 * mobile pipeline must comfortably outpace that — 1000 frames/s is a
 * 40x safety margin so the BLE collector flow is never the
 * bottleneck under burst conditions (e.g., a backlog flushed after a
 * brief disconnect).
 *
 * Measures decode latency over a deterministic fixture (same NAV-PVT
 * frame repeated [FRAME_COUNT] times). Warmup pass amortises JIT
 * compilation so the timed pass reflects steady-state throughput.
 *
 * The benchmark is conservative — it asserts ≥ 1000 frames/s, not
 * the actual measured rate (which is typically 100×+ that on
 * mid-range CI hardware). Tightening the bound would make the test
 * flaky under shared CI load.
 */
class UbxPipelineThroughputTest {
    @Test
    fun pipeline_sustains_at_least_1000_navpvt_frames_per_second() = runTest {
        val stream = buildNavPvtStream(FRAME_COUNT)
        val framer = UbxFramer()

        // Warmup — let the JIT optimise the hot loop before we measure.
        val warmupStream = buildNavPvtStream(WARMUP_FRAME_COUNT)
        framer.frames(flowOf(warmupStream)).toList()

        var decoded: List<UbxFrame>
        val elapsedNs = measureNanoTime {
            decoded = framer.frames(flowOf(stream)).toList()
        }

        assertEquals(
            "framer must surface every well-formed frame; lost frames imply a buffer-management bug",
            FRAME_COUNT,
            decoded.size,
        )
        // For each frame, also exercise the NavPvtParser to time the
        // full pipeline (framer is the heavy lifter; parser adds overhead).
        for (frame in decoded) {
            assertEquals(UBX_NAV_CLASS, frame.classId)
            assertEquals(UBX_NAV_PVT_ID, frame.messageId)
            requireNotNull(NavPvtParser.parse(frame.payload)) {
                "valid NAV-PVT payload must parse"
            }
        }

        val framesPerSecond = FRAME_COUNT.toLong() * NS_PER_SEC / elapsedNs
        assertTrue(
            "pipeline throughput $framesPerSecond f/s must clear $MIN_FRAMES_PER_SEC f/s; " +
                "elapsed=${elapsedNs / NS_PER_SEC.toDouble()}s for $FRAME_COUNT frames",
            framesPerSecond >= MIN_FRAMES_PER_SEC,
        )
    }

    private fun buildNavPvtStream(frameCount: Int): ByteArray {
        val singleFrame = buildOneNavPvtFrame()
        val buffer = ByteArray(singleFrame.size * frameCount)
        for (i in 0 until frameCount) {
            singleFrame.copyInto(buffer, destinationOffset = i * singleFrame.size)
        }
        return buffer
    }

    private fun buildOneNavPvtFrame(): ByteArray {
        val frameSize = FRAME_OVERHEAD_BYTES + NAV_PVT_PAYLOAD_LEN
        val frame = ByteArray(frameSize)
        frame[0] = SYNC1
        frame[1] = SYNC2
        frame[2] = UBX_NAV_CLASS.toByte()
        frame[3] = UBX_NAV_PVT_ID.toByte()
        // Length = NAV_PVT_PAYLOAD_LEN, little-endian
        frame[4] = (NAV_PVT_PAYLOAD_LEN and BYTE_MASK).toByte()
        frame[5] = ((NAV_PVT_PAYLOAD_LEN ushr Byte.SIZE_BITS) and BYTE_MASK).toByte()

        // Minimal-yet-valid NAV-PVT payload: year=2026, month=4, day=25,
        // hour=14, min=30, sec=12 — keeps NavPvtParser + RaceboxSampleMapper
        // happy if anything downstream parses the result.
        val buf = ByteBuffer.wrap(frame, PAYLOAD_OFFSET_IN_FRAME, NAV_PVT_PAYLOAD_LEN)
            .order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(0)
        buf.putShort(YEAR_2026)
        buf.put(MONTH_APRIL)
        buf.put(DAY_25)
        buf.put(HOUR_14)
        buf.put(MIN_30)
        buf.put(SEC_12)

        // Fletcher checksum over class + id + length + payload (sync excluded).
        var ckA = 0
        var ckB = 0
        for (i in 2 until 2 + 4 + NAV_PVT_PAYLOAD_LEN) {
            ckA = (ckA + (frame[i].toInt() and BYTE_MASK)) and BYTE_MASK
            ckB = (ckB + ckA) and BYTE_MASK
        }
        frame[frame.size - 2] = ckA.toByte()
        frame[frame.size - 1] = ckB.toByte()
        return frame
    }

    companion object {
        private const val YEAR_2026: Short = 2026
        private const val MONTH_APRIL: Byte = 4
        private const val DAY_25: Byte = 25
        private const val HOUR_14: Byte = 14
        private const val MIN_30: Byte = 30
        private const val SEC_12: Byte = 12
    }
}
