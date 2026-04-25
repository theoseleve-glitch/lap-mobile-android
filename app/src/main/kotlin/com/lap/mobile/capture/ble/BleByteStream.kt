package com.lap.mobile.capture.ble

import kotlinx.coroutines.flow.Flow

/**
 * Routes raw BLE notification payloads from the resolved
 * [UartCharacteristics.txUuid] into a `Flow<ByteArray>` (T105).
 * Thin pass-through over [BleNotificationSource] — there's no
 * transformation here, the framer (T106) owns UBX reassembly
 * downstream.
 *
 * The TX UUID lookup is encapsulated so consumers (T106 framer,
 * future device-management surface) don't have to repeat the same
 * `characteristics.txUuid` reference at every call site.
 */
class BleByteStream(
    private val source: BleNotificationSource,
    private val characteristics: UartCharacteristics,
) {
    fun bytes(): Flow<ByteArray> = source.notifications(characteristics.txUuid)
}
