package com.lap.mobile.capture.ble

import kotlinx.coroutines.flow.Flow

/**
 * Provider-agnostic seam over the actual BLE scan engine
 * (`BluetoothLeScannerCompat` from `no.nordicsemi.android.support.v18.scanner`).
 * The Android impl ([NordicBleScannerSource]) wires the real callback;
 * tests pass a fixture flow.
 *
 * Service-UUID filtering is the caller's job — pass the RaceBox
 * service UUID literal and the source returns a flow of every
 * advertiser matching it (and only those — system-side filtering
 * keeps the radio cost bounded).
 */
fun interface BleScannerSource {
    fun scan(serviceUuid: String): Flow<RaceboxAdvertiser>
}
