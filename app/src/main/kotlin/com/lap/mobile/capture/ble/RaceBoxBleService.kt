package com.lap.mobile.capture.ble

import kotlinx.coroutines.flow.Flow

/**
 * Surfaces RaceBox-class advertisers within BLE range (T102). Thin
 * pass-through over a [BleScannerSource] — the scanner-engine
 * specifics live behind the seam (Nordic's `BluetoothLeScannerCompat`
 * for the production impl), this class only owns the service-UUID
 * filter literal.
 *
 * The default UUID is RaceBox's vendor-defined GATT service ID per
 * their integration documentation; constructor-injected so a future
 * variant (or a custom RaceBox firmware) can be probed by passing a
 * different UUID without subclassing.
 *
 * Returned [Flow] is hot-while-collected — the source manages
 * `startScan` / `stopScan` against the OS BLE stack so callers
 * just collect / cancel.
 */
class RaceBoxBleService(
    private val scannerSource: BleScannerSource,
    private val serviceUuid: String = RACEBOX_SERVICE_UUID,
) {
    fun scan(): Flow<RaceboxAdvertiser> = scannerSource.scan(serviceUuid)

    companion object {
        // RaceBox's vendor-defined GATT service UUID per their integration docs.
        const val RACEBOX_SERVICE_UUID: String = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
    }
}
