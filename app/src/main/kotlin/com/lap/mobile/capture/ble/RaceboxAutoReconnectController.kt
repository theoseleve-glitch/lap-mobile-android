package com.lap.mobile.capture.ble

import com.lap.mobile.data.LastUsedRacebox
import com.lap.mobile.data.LastUsedRaceboxLookup

/**
 * Single addressed connect attempt seam (T125) — the auto-reconnect
 * controller curries the saved RaceBox MAC into this so the
 * underlying T119 retry loop can stay address-agnostic.
 *
 * `attemptNumber` is 1-indexed for the same reason as
 * [BleReconnectAttempt]: human-readable logs and a simple
 * backoff = base × attemptNumber formula if a future strategy needs it.
 */
fun interface AddressedBleConnectAttempt {
    suspend fun connect(address: String, attemptNumber: Int): Boolean
}

/**
 * Outcome of [RaceboxAutoReconnectController.attemptAutoReconnect]:
 *
 *  - [Connected] — a saved device existed and we re-paired with it.
 *  - [Failed] — a saved device existed but every retry failed; the
 *    UI should drop into the manual scan flow (T122/T123) and the
 *    fallback path (T120) takes over for any in-progress session.
 *  - [NoSavedDevice] — no prior pairing on this install; the user
 *    must scan and pick a device themselves.
 */
sealed interface AutoReconnectOutcome {
    data class Connected(val device: LastUsedRacebox) : AutoReconnectOutcome
    data class Failed(val device: LastUsedRacebox) : AutoReconnectOutcome
    data object NoSavedDevice : AutoReconnectOutcome
}

/**
 * Auto-reconnect controller invoked at session start (T125). Composes
 * the persisted last-used device ([LastUsedRaceboxLookup], T124) with
 * T119's bounded retry policy ([RaceboxReconnectPolicy]) — keep both
 * pieces independently testable; the controller is just the wiring.
 *
 * Currying the saved address into the per-attempt closure means the
 * T119 policy itself stays address-agnostic and reusable for both
 * mid-session reconnect (T119 caller wires the live device) and
 * session-start auto-reconnect (here, wires the persisted device).
 */
class RaceboxAutoReconnectController(
    private val lookup: LastUsedRaceboxLookup,
    private val attempt: AddressedBleConnectAttempt,
) {
    suspend fun attemptAutoReconnect(): AutoReconnectOutcome {
        val saved = lookup.get() ?: return AutoReconnectOutcome.NoSavedDevice
        val policy = RaceboxReconnectPolicy(
            BleReconnectAttempt { attemptNumber -> attempt.connect(saved.address, attemptNumber) },
        )
        return if (policy.reconnect()) {
            AutoReconnectOutcome.Connected(saved)
        } else {
            AutoReconnectOutcome.Failed(saved)
        }
    }
}
