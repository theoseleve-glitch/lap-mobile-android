package com.lap.mobile.capture.ble

/**
 * Per-connection set of GATT characteristic UUIDs the peripheral
 * exposed under a single service. The Nordic / Android impl walks
 * [android.bluetooth.BluetoothGatt.getServices] and turns each
 * `BluetoothGattService` into one of these.
 */
data class GattServiceSnapshot(
    val serviceUuid: String,
    val characteristicUuids: Set<String>,
)

/**
 * Provider-agnostic seam over `BluetoothGatt.getServices()`.
 * [UartServiceDiscovery] consults this after the connection enters
 * `CONNECTED` (T103) and the GATT service-discovery callback fires.
 * Tests pass a fixture list; the Android-side adapter wraps the
 * real GATT.
 */
fun interface GattServiceSnapshotSource {
    suspend fun snapshot(): List<GattServiceSnapshot>
}

/**
 * Resolves the Nordic UART Service handles on the connected RaceBox
 * peripheral (T104). Looks up [UartUuids.SERVICE] in the provided
 * snapshot, then verifies both [UartUuids.TX] (notify) and
 * [UartUuids.RX] (write) characteristics are present.
 *
 * The three-armed [UartDiscoveryResult] distinguishes:
 *  - **Found**: ready to subscribe / write — the happy path that
 *    feeds T105's packet stream.
 *  - **NotFound**: the peripheral didn't expose NUS at all (wrong
 *    device or unrelated peripheral on the same MAC).
 *  - **IncompleteService**: NUS exists but a characteristic is
 *    missing — useful for forensics if a RaceBox firmware revision
 *    ships a partial UART surface; the UI can render a specific
 *    "firmware update needed" message instead of the generic
 *    "device unsupported" branch.
 */
class UartServiceDiscovery(
    private val source: GattServiceSnapshotSource,
) {
    suspend fun discover(): UartDiscoveryResult {
        val uartService = source.snapshot().firstOrNull { it.serviceUuid == UartUuids.SERVICE }
            ?: return UartDiscoveryResult.NotFound

        val missing = mutableSetOf<String>()
        if (UartUuids.TX !in uartService.characteristicUuids) missing.add(UartUuids.TX)
        if (UartUuids.RX !in uartService.characteristicUuids) missing.add(UartUuids.RX)
        if (missing.isNotEmpty()) return UartDiscoveryResult.IncompleteService(missing.toSet())

        return UartDiscoveryResult.Found(
            characteristics = UartCharacteristics(
                serviceUuid = UartUuids.SERVICE,
                txUuid = UartUuids.TX,
                rxUuid = UartUuids.RX,
            ),
        )
    }
}

sealed class UartDiscoveryResult {
    data class Found(val characteristics: UartCharacteristics) : UartDiscoveryResult()
    data object NotFound : UartDiscoveryResult()
    data class IncompleteService(val missing: Set<String>) : UartDiscoveryResult()
}
