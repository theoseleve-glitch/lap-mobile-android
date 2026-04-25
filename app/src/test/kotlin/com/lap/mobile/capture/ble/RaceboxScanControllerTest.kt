package com.lap.mobile.capture.ble

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DEFAULT_TIMEOUT_MS: Long = 30_000L
private const val SERVICE_UUID: String = "0000feed-0000-1000-8000-00805f9b34fb"
private val ADV_A: RaceboxAdvertiser =
    RaceboxAdvertiser(address = "AA:BB:CC:DD:EE:01", name = "RaceBox A", rssiDbm = -55)
private val ADV_B: RaceboxAdvertiser =
    RaceboxAdvertiser(address = "AA:BB:CC:DD:EE:02", name = "RaceBox B", rssiDbm = -70)

@OptIn(ExperimentalCoroutinesApi::class)
class RaceboxScanControllerTest {
    @Test
    fun returns_timeout_when_no_advertiser_emits_within_window() = runTest {
        val controller = RaceboxScanController(neverEmittingScanner(), SERVICE_UUID)

        val result = controller.scanWithTimeout(DEFAULT_TIMEOUT_MS)

        assertTrue("expected Timeout, was $result", result is RaceboxScanResult.Timeout)
    }

    @Test
    fun returns_found_with_single_advertiser_emitted_inside_window() = runTest {
        val controller = RaceboxScanController(emitsThenSuspends(listOf(ADV_A)), SERVICE_UUID)

        val result = controller.scanWithTimeout(DEFAULT_TIMEOUT_MS)

        assertEquals(RaceboxScanResult.Found(listOf(ADV_A)), result)
    }

    @Test
    fun collects_every_advertiser_emitted_before_timeout() = runTest {
        val controller = RaceboxScanController(emitsThenSuspends(listOf(ADV_A, ADV_B)), SERVICE_UUID)

        val result = controller.scanWithTimeout(DEFAULT_TIMEOUT_MS)

        assertEquals(RaceboxScanResult.Found(listOf(ADV_A, ADV_B)), result)
    }

    @Test
    fun completes_at_or_before_the_configured_timeout() = runTest {
        val controller = RaceboxScanController(neverEmittingScanner(), SERVICE_UUID)

        val before = testScheduler.currentTime
        controller.scanWithTimeout(DEFAULT_TIMEOUT_MS)
        val elapsed = testScheduler.currentTime - before

        // Should bound the scan at exactly the configured timeout.
        assertEquals(DEFAULT_TIMEOUT_MS, elapsed)
    }

    private fun neverEmittingScanner(): BleScannerSource = BleScannerSource {
        flow<RaceboxAdvertiser> { awaitCancellation() }
    }

    private fun emitsThenSuspends(advertisers: List<RaceboxAdvertiser>): BleScannerSource = BleScannerSource {
        flow {
            advertisers.forEach { emit(it) }
            awaitCancellation()
        }
    }
}
