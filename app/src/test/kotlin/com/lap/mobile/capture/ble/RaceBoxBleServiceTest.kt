package com.lap.mobile.capture.ble

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val FIXTURE_ADDR_A: String = "AA:BB:CC:DD:EE:FF"
private const val FIXTURE_ADDR_B: String = "11:22:33:44:55:66"
private const val FIXTURE_NAME_A: String = "RaceBox Mini 12345"
private const val FIXTURE_RSSI_A: Int = -55
private const val FIXTURE_RSSI_B: Int = -72
private const val EXPECTED_ADVERTISER_COUNT: Int = 2

/**
 * Unit tests for [RaceBoxBleService] (T102). The service is a thin
 * pass-through over [BleScannerSource]; the only logic worth
 * testing JVM-side is that the configured UUID is what gets
 * forwarded, and the flow plumbing doesn't drop emissions. Live
 * scan behavior is `[PENDING: device-required]`.
 */
class RaceBoxBleServiceTest {
    @Test
    fun scan_forwards_configured_service_uuid_to_source() = runTest {
        var capturedUuid: String? = null
        val source = BleScannerSource { uuid ->
            capturedUuid = uuid
            flowOf()
        }
        val service = RaceBoxBleService(scannerSource = source)

        service.scan().toList()

        assertEquals(RaceBoxBleService.RACEBOX_SERVICE_UUID, capturedUuid)
    }

    @Test
    fun scan_propagates_advertisers_from_source() = runTest {
        val advertisers = listOf(
            RaceboxAdvertiser(address = FIXTURE_ADDR_A, name = FIXTURE_NAME_A, rssiDbm = FIXTURE_RSSI_A),
            RaceboxAdvertiser(address = FIXTURE_ADDR_B, name = null, rssiDbm = FIXTURE_RSSI_B),
        )
        val source = BleScannerSource { _ -> flowOf(*advertisers.toTypedArray()) }
        val service = RaceBoxBleService(scannerSource = source)

        val collected = service.scan().toList()

        assertEquals(EXPECTED_ADVERTISER_COUNT, collected.size)
        assertEquals(advertisers, collected)
    }

    @Test
    fun custom_service_uuid_overrides_default() = runTest {
        val customUuid = "00001234-0000-1000-8000-00805f9b34fb"
        var capturedUuid: String? = null
        val source = BleScannerSource { uuid ->
            capturedUuid = uuid
            flowOf()
        }
        val service = RaceBoxBleService(scannerSource = source, serviceUuid = customUuid)

        service.scan().toList()

        assertEquals(customUuid, capturedUuid)
    }

    @Test
    fun scan_with_no_advertisers_returns_empty_list() = runTest {
        val source = BleScannerSource { _ -> flowOf() }
        val service = RaceBoxBleService(scannerSource = source)

        val collected = service.scan().toList()

        assertTrue("no advertisers should yield an empty list", collected.isEmpty())
    }
}
