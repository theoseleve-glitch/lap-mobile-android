package com.lap.mobile.ui.ble

import com.lap.mobile.capture.ble.BleConnectionState

/**
 * Display variants for the dashboard-header RaceBox status pill
 * (T130). The Compose layer maps each variant to a colour/icon
 * combination; this kernel just decides the bucket. Hoisted out of
 * the composable so the bucket choice is JVM-tested.
 *
 *  - [NEUTRAL] — DISCONNECTED: idle, nothing in flight.
 *  - [ACTIVE] — SCANNING / CONNECTING: progress, animation cue.
 *  - [SUCCESS] — CONNECTED: link healthy, samples flowing.
 *  - [WARNING] — LOST: was connected, link dropped; T119 reconnect
 *    is presumed in flight or the user needs to act.
 */
enum class RaceboxStatusVariant {
    NEUTRAL,
    ACTIVE,
    SUCCESS,
    WARNING,
}

object RaceboxStatusDisplay {
    fun variantFor(state: BleConnectionState): RaceboxStatusVariant = when (state) {
        BleConnectionState.DISCONNECTED -> RaceboxStatusVariant.NEUTRAL
        BleConnectionState.SCANNING, BleConnectionState.CONNECTING -> RaceboxStatusVariant.ACTIVE
        BleConnectionState.CONNECTED -> RaceboxStatusVariant.SUCCESS
        BleConnectionState.LOST -> RaceboxStatusVariant.WARNING
    }
}
