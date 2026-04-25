package com.lap.mobile.capture.ble

/**
 * Lifecycle state for the RaceBox BLE link (T103). The states cover
 * the full discover-connect-recover cycle the readiness UI (T097) and
 * the in-session telemetry guard (T120 fallback) need to react to.
 *
 *  - [DISCONNECTED] — terminal idle state; no scan or connection
 *    attempt in flight.
 *  - [SCANNING] — actively probing for advertisers (T102).
 *  - [CONNECTING] — peripheral picked, GATT handshake in progress.
 *  - [CONNECTED] — peripheral fully bound, packets flowing.
 *  - [LOST] — was [CONNECTED], the link dropped unexpectedly. T119's
 *    reconnect attempts run from this state.
 */
enum class BleConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    LOST,
}
