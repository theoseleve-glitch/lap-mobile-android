package com.lap.mobile.capture.ble

private const val DEFAULT_MAX_ATTEMPTS: Int = 3

/**
 * Single connect attempt seam (T119). The implementation is platform-
 * specific (Nordic BleManager.connect()) but the policy that decides
 * whether to retry is pure Kotlin — keeping this fun-interface lets
 * the retry logic be JVM-tested without a BLE peripheral.
 *
 * `attemptNumber` is 1-indexed so logs and analytics match what a
 * human would say ("attempt 2 of 3"); it lets a future backoff
 * strategy compute delay = base × attemptNumber without re-deriving
 * the count externally.
 */
fun interface BleReconnectAttempt {
    suspend fun connect(attemptNumber: Int): Boolean
}

/**
 * Bounded-retry reconnect policy for the RaceBox BLE link (T119). On
 * disconnect the controller calls [reconnect] which invokes the
 * underlying [BleReconnectAttempt] up to [maxAttempts] times until one
 * succeeds. Returns true on success, false when all attempts fail.
 *
 * Stateless across calls — each [reconnect] invocation gets a fresh
 * attempt budget. The cap exists so a permanently-out-of-range device
 * doesn't loop forever burning the radio; the in-session disconnect
 * UX (T120 — journal a gap, fall back to phone GPS) takes over after
 * the policy gives up.
 */
class RaceboxReconnectPolicy(
    private val attempt: BleReconnectAttempt,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
) {
    suspend fun reconnect(): Boolean {
        for (i in 1..maxAttempts) {
            if (attempt.connect(i)) return true
        }
        return false
    }
}
