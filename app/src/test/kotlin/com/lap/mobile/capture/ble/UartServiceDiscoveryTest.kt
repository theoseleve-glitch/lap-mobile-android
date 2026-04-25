package com.lap.mobile.capture.ble

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [UartServiceDiscovery] (T104). The discovery walks
 * a fixture list of [GattServiceSnapshot] (representing the
 * services the peripheral exposed) and either finds the Nordic UART
 * Service with both characteristics, reports the absence outright,
 * or reports an incomplete service — useful for forensics when a
 * RaceBox firmware revision exposes a partial NUS.
 */
class UartServiceDiscoveryTest {
    @Test
    fun discovers_complete_uart_service() = runTest {
        val source = GattServiceSnapshotSource {
            listOf(
                GattServiceSnapshot(
                    serviceUuid = UartUuids.SERVICE,
                    characteristicUuids = setOf(UartUuids.TX, UartUuids.RX),
                ),
            )
        }
        val discovery = UartServiceDiscovery(source)

        val result = discovery.discover()

        assertTrue("expected Found, got $result", result is UartDiscoveryResult.Found)
        val chars = (result as UartDiscoveryResult.Found).characteristics
        assertEquals(UartUuids.SERVICE, chars.serviceUuid)
        assertEquals(UartUuids.TX, chars.txUuid)
        assertEquals(UartUuids.RX, chars.rxUuid)
    }

    @Test
    fun no_uart_service_returns_not_found() = runTest {
        val source = GattServiceSnapshotSource {
            listOf(
                GattServiceSnapshot(
                    serviceUuid = "00001800-0000-1000-8000-00805f9b34fb",
                    characteristicUuids = setOf("00002a00-0000-1000-8000-00805f9b34fb"),
                ),
            )
        }
        val discovery = UartServiceDiscovery(source)

        val result = discovery.discover()

        assertEquals(UartDiscoveryResult.NotFound, result)
    }

    @Test
    fun missing_tx_characteristic_returns_incomplete() = runTest {
        val source = GattServiceSnapshotSource {
            listOf(
                GattServiceSnapshot(
                    serviceUuid = UartUuids.SERVICE,
                    characteristicUuids = setOf(UartUuids.RX),
                ),
            )
        }
        val discovery = UartServiceDiscovery(source)

        val result = discovery.discover()

        assertTrue("expected Incomplete, got $result", result is UartDiscoveryResult.IncompleteService)
        assertEquals(setOf(UartUuids.TX), (result as UartDiscoveryResult.IncompleteService).missing)
    }

    @Test
    fun missing_rx_characteristic_returns_incomplete() = runTest {
        val source = GattServiceSnapshotSource {
            listOf(
                GattServiceSnapshot(
                    serviceUuid = UartUuids.SERVICE,
                    characteristicUuids = setOf(UartUuids.TX),
                ),
            )
        }
        val discovery = UartServiceDiscovery(source)

        val result = discovery.discover()

        assertTrue("expected Incomplete, got $result", result is UartDiscoveryResult.IncompleteService)
        assertEquals(setOf(UartUuids.RX), (result as UartDiscoveryResult.IncompleteService).missing)
    }

    @Test
    fun empty_service_list_returns_not_found() = runTest {
        val source = GattServiceSnapshotSource { emptyList() }
        val discovery = UartServiceDiscovery(source)

        assertEquals(UartDiscoveryResult.NotFound, discovery.discover())
    }
}
