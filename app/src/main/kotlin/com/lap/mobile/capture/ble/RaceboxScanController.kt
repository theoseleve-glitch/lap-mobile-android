package com.lap.mobile.capture.ble

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Outcome of a bounded BLE scan (T122). [Found] carries every
 * advertiser observed during the window in arrival order; [Timeout]
 * means the window elapsed before any advertiser surfaced — the UI
 * (T123) maps this to the "no RaceBox detected" empty-state CTA.
 */
sealed interface RaceboxScanResult {
    data class Found(val advertisers: List<RaceboxAdvertiser>) : RaceboxScanResult
    data object Timeout : RaceboxScanResult
}

/**
 * Bounded BLE scan controller (T122). Wraps a [BleScannerSource]
 * (T102) with a configurable timeout — 30s in production, smaller in
 * tests via virtual time. After the window elapses the underlying
 * scan is cancelled (the cold flow's structured-concurrency teardown
 * stops the radio); whatever advertisers were collected before the
 * cancellation are returned in [RaceboxScanResult.Found].
 *
 * [withTimeoutOrNull] returns null on timeout, but we don't depend on
 * it here — what matters is whether any advertiser made it into the
 * accumulator, which the surrounding scope can read regardless of
 * cancellation. That collapses the two cases (collected-then-timeout
 * vs. collected-and-still-scanning) into the same [Found] return so
 * the UI doesn't have to handle a "found but timed out" hybrid state.
 */
class RaceboxScanController(
    private val source: BleScannerSource,
    private val serviceUuid: String,
) {
    suspend fun scanWithTimeout(timeoutMs: Long): RaceboxScanResult {
        val collected = mutableListOf<RaceboxAdvertiser>()
        withTimeoutOrNull(timeoutMs) {
            source.scan(serviceUuid).toList(collected)
        }
        return if (collected.isEmpty()) RaceboxScanResult.Timeout else RaceboxScanResult.Found(collected.toList())
    }
}
