package com.lap.mobile.dashboard

/**
 * Pure-function calculator: sliding window of sample timestamps →
 * achieved-Hz (T199).
 *
 * For N samples spanning T seconds, achieved-Hz = (N - 1) / T.
 * Using N-1 intervals (not N) means a window of exactly 11 samples
 * spanning exactly 1 second reports 10Hz — matches the user's
 * mental model of "the system is producing 10 samples per second,
 * which is 10 intervals across the span".
 *
 * The list is assumed in chronological order. The orchestrator
 * (Android shell) is responsible for keeping the window slim — a
 * 1-second window at 50Hz IMU is ~50 timestamps, trivial to pass
 * each composition.
 *
 * Defensive cases (locked by [SampleRateCalculatorTest]):
 *   - Empty or single-sample input → 0 Hz (no interval to measure).
 *   - Zero span (all samples at same epoch) → 0 Hz, NOT Infinity.
 *
 * Pure function — no Android, no time, no I/O — so the contract is
 * locked by JVM unit tests. Calculator output is "achieved", not
 * "configured" — the dashboard intentionally surfaces the real rate
 * so the driver can spot a degraded pipeline (BLE drops, device
 * thermal throttling, sensor sleeping).
 */
object SampleRateCalculator {
    fun calculateHz(sampleTimestampsMs: List<Long>): Double {
        if (sampleTimestampsMs.size < MIN_SAMPLES_FOR_RATE) return 0.0
        val spanMs = sampleTimestampsMs.last() - sampleTimestampsMs.first()
        if (spanMs <= 0L) return 0.0
        val intervals = sampleTimestampsMs.size - 1
        return (intervals.toDouble() * MS_PER_SECOND) / spanMs.toDouble()
    }

    private const val MIN_SAMPLES_FOR_RATE: Int = 2
    private const val MS_PER_SECOND: Double = 1_000.0
}
