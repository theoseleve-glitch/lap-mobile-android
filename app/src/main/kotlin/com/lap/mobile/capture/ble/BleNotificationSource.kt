package com.lap.mobile.capture.ble

import kotlinx.coroutines.flow.Flow

/**
 * Provider-agnostic seam over a single GATT-notification subscription
 * (T105). The Android impl wires a `BleManager`
 * `setNotificationCallback` and `enableNotifications` against the
 * resolved [UartCharacteristics.txUuid]; tests pass a fixture flow
 * of `ByteArray` chunks straight through.
 *
 * Each `ByteArray` is one BLE notification payload — possibly a
 * fragment of a larger UBX frame the framer (T106) reassembles.
 * No transformation happens here; the seam is intentionally raw so
 * test fixtures can use captured wire-format byte arrays verbatim.
 */
fun interface BleNotificationSource {
    fun notifications(characteristicUuid: String): Flow<ByteArray>
}
