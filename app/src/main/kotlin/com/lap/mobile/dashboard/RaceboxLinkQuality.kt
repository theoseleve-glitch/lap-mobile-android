package com.lap.mobile.dashboard

import com.lap.mobile.capture.ble.BleConnectionState

/**
 * RaceBox BLE-link quality band for the dashboard's
 * red/yellow/green pill (T200).
 *
 * Three discrete states matching driver expectations — collapses
 * the [BleConnectionState] 5-state operational machine into the
 * actionable signals:
 *   - [Connected]:    packets flowing, trust the live readouts.
 *   - [Pending]:      working on it (scanning, connecting, or
 *                     T119 reconnect after LOST) — wait briefly,
 *                     no driver action required.
 *   - [Disconnected]: terminal idle, no recovery in progress —
 *                     driver action required (tap connect, check
 *                     device power, move closer).
 */
enum class RaceboxLinkQuality {
    Connected,
    Pending,
    Disconnected,
}

/**
 * Pure mapping function: [BleConnectionState] → [RaceboxLinkQuality]
 * (T200 kernel).
 *
 * Why three bands not five: the dashboard is a glance surface. A
 * driver mid-corner doesn't need to know SCANNING vs CONNECTING vs
 * LOST — those are operational states for reconnect machinery
 * (T102, T119), not signals the driver acts on.
 *
 * LOST → [RaceboxLinkQuality.Pending] (not Disconnected) because
 * LOST is "we WERE connected, T119's reconnect attempts are
 * running from this state". Driver's right action is wait briefly,
 * not "go fix the device". Showing Disconnected for LOST would
 * trigger needless intervention during a routine micro-disconnect
 * (e.g., a brief antenna shadow when the car passes a building).
 *
 * DISCONNECTED → [RaceboxLinkQuality.Disconnected] is the only
 * red band — terminal idle, no scan/connect in flight, action
 * required.
 */
object RaceboxLinkQualityClassifier {
    fun classify(state: BleConnectionState): RaceboxLinkQuality = when (state) {
        BleConnectionState.CONNECTED -> RaceboxLinkQuality.Connected
        BleConnectionState.SCANNING,
        BleConnectionState.CONNECTING,
        BleConnectionState.LOST,
        -> RaceboxLinkQuality.Pending
        BleConnectionState.DISCONNECTED -> RaceboxLinkQuality.Disconnected
    }
}
