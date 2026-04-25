package com.lap.mobile.capture.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the [BleConnectionState] for the RaceBox link (T103). The
 * state is mutated by the BLE manager (Nordic) callbacks and
 * observed via [state] by:
 *
 *  - [com.lap.mobile.capture.RaceboxConnectionStatus] (T092
 *    preflight) for the readiness gate.
 *  - The session-time guard (T120) that flips to phone GPS when
 *    the link drops mid-session.
 *  - The recording UI badge that mirrors the live link state.
 *
 * StateFlow rather than SharedFlow so late subscribers get the
 * current state immediately — these consumers care about *what is
 * the state right now*, not a history of transitions.
 */
class BleConnectionStateHolder {
    private val _state: MutableStateFlow<BleConnectionState> =
        MutableStateFlow(BleConnectionState.DISCONNECTED)
    val state: StateFlow<BleConnectionState> = _state.asStateFlow()

    fun update(newState: BleConnectionState) {
        _state.value = newState
    }
}
