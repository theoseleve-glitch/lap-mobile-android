package com.lap.mobile.capture.ble

import no.nordicsemi.android.ble.BleManager

/**
 * Smoke-test marker for the Nordic Android BLE library integration
 * (T101). The mere fact this file compiles proves the dependency
 * resolved; T102 onwards builds the real `RaceBoxBleService` on
 * top of [BleManager].
 *
 * `BleManager` is Nordic's connection-side abstraction (handles
 * MTU negotiation, GATT callbacks, queue serialisation); the
 * scanner-side artifact (`no.nordicsemi.android.support.v18:scanner`)
 * lands when T102 needs it.
 */
internal val nordicBleManagerClass: Class<BleManager> = BleManager::class.java
