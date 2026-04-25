package com.lap.mobile.capture.ble

/**
 * Resolved Nordic UART Service handles for the RaceBox link
 * (T104). Carries the three GATT identifiers the packet stream
 * (T105) needs:
 *
 *  - [serviceUuid] — the UART service itself
 *  - [txUuid] — peripheral → app NOTIFY characteristic (UBX bytes
 *    arrive here)
 *  - [rxUuid] — app → peripheral WRITE characteristic (config / cmd
 *    bytes leave here)
 *
 * Naming convention from the peripheral's perspective: TX is what
 * the peripheral *transmits* to us. The Android peer reads TX via
 * notifications and writes commands to RX.
 */
data class UartCharacteristics(
    val serviceUuid: String,
    val txUuid: String,
    val rxUuid: String,
)

object UartUuids {
    // Nordic UART Service (NUS) — the service RaceBox advertises.
    const val SERVICE: String = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"

    // App → peripheral (commands, config). Application writes here.
    const val RX: String = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"

    // Peripheral → app (telemetry). Application enables notifications here.
    const val TX: String = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"
}
