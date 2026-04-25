package com.lap.mobile.capture.ble

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val FIXTURE_FRAME_COUNT: Int = 50
private const val SUB_MTU_CHUNK: Int = 7
private const val FRAME_BOUNDARY_CHUNK: Int = 100
private const val SOURCE_CLOCK_RACEBOX: String = "racebox_gnss"

/**
 * JVM end-to-end test for the [MockRaceboxPeripheral] fixture
 * (T129). The full Android-emulator BLE integration stays
 * `[PENDING: device-required]`; this test exercises the same
 * fixture through the framer + parser + sample-mapper so the device-
 * side wiring has a known-good byte source to anchor against.
 *
 * Two chunk sizes pin the cross-chunk reassembly path that the real
 * BLE stack hits: 7 bytes (well below the 8-byte minimum frame size,
 * forcing every frame to span multiple chunks) and 100 bytes (frame-
 * boundary-aligned for the fixture).
 */
class MockRaceboxPeripheralIntegrationTest {
    @Test
    fun framer_decodes_every_frame_when_chunks_are_smaller_than_minimum_frame_size() = runTest {
        val flow = MockRaceboxPeripheral.navPvtFlow(
            frameCount = FIXTURE_FRAME_COUNT,
            chunkBytes = SUB_MTU_CHUNK,
        )

        val frames = UbxFramer().frames(flow).toList()

        assertEquals(FIXTURE_FRAME_COUNT, frames.size)
        assertTrue(frames.all { it.classId == NavPvtParser.CLASS_ID })
        assertTrue(frames.all { it.messageId == NavPvtParser.MESSAGE_ID })
    }

    @Test
    fun parser_round_trips_every_decoded_frame_to_telemetry_sample() = runTest {
        val flow = MockRaceboxPeripheral.navPvtFlow(
            frameCount = FIXTURE_FRAME_COUNT,
            chunkBytes = FRAME_BOUNDARY_CHUNK,
        )

        val samples = UbxFramer().frames(flow).toList()
            .mapNotNull { NavPvtParser.parse(it.payload) }
            .map { it.toTelemetrySample() }

        assertEquals(FIXTURE_FRAME_COUNT, samples.size)
        assertTrue(samples.all { it.tSourceClock == SOURCE_CLOCK_RACEBOX })
    }
}
