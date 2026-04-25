package com.lap.mobile.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

private const val HZ_TOLERANCE: Double = 0.01

/**
 * Locks the contract for [SampleRateCalculator] (T199) — the pure
 * function that turns a sliding window of sample timestamps into
 * the achieved-Hz number rendered by
 * [com.lap.mobile.ui.dashboard.SampleRateIndicator].
 *
 * Why "achieved" Hz, not "configured" Hz: the dashboard wants to
 * tell the driver if the recording cadence is actually healthy.
 * A 10Hz-configured pipeline that's only delivering 7Hz (because
 * the device throttled, the BLE link dropped packets, or the
 * sensor is sleeping) is a problem the driver should see — they
 * can decide whether to keep capturing or restart. Configured Hz
 * would lie about that.
 *
 * Computation: for N samples spanning T seconds, achieved-Hz =
 * (N - 1) / T. Using N-1 intervals (not N) means a window of
 * exactly 11 samples spanning exactly 1 second reports 10Hz —
 * matches the user's mental model of "the system is producing
 * 10 samples per second, which is 10 intervals across the span".
 *
 * Defensive cases (locked here):
 *   - Empty list → 0 Hz (caller renders "—" or "0 Hz" placeholder).
 *   - Single sample → 0 Hz (no interval to measure).
 *   - All same-timestamp samples → 0 Hz (zero span; defensive
 *     against a paused sampler emitting batched timestamps).
 *
 * Pure function — no Android, no time, no I/O — so the contract is
 * locked by JVM unit tests and the Android shell can't accidentally
 * change semantics by changing how it observes its environment.
 */
class SampleRateCalculatorTest {
    @Test
    fun empty_window_yields_zero_hz_so_pre_session_indicator_reads_zero_not_NaN_or_infinity() {
        assertEquals(0.0, SampleRateCalculator.calculateHz(sampleTimestampsMs = emptyList()), HZ_TOLERANCE)
    }

    @Test
    fun single_sample_yields_zero_hz_because_one_sample_has_no_interval_to_measure() {
        // Defensive: rate requires at least two samples (one
        // interval). A bootstrap state with exactly one received
        // sample shouldn't render as Infinity or NaN.
        assertEquals(
            0.0,
            SampleRateCalculator.calculateHz(sampleTimestampsMs = listOf(1000L)),
            HZ_TOLERANCE,
        )
    }

    @Test
    fun two_samples_one_hundred_ms_apart_yield_ten_hz_so_the_minimum_two_sample_window_works() {
        // 1 interval / 0.1 s = 10 Hz.
        val samples = listOf(0L, 100L)

        assertEquals(10.0, SampleRateCalculator.calculateHz(sampleTimestampsMs = samples), HZ_TOLERANCE)
    }

    @Test
    fun eleven_samples_spanning_exactly_one_second_yield_ten_hz_per_drivers_mental_model_of_intervals_per_second() {
        // Headline T199 invariant: 10Hz = 10 intervals per second =
        // 11 samples across 1 second. Using N-1 intervals means the
        // edge samples bracket the duration cleanly.
        val samples = (0..10).map { it * 100L }

        assertEquals(10.0, SampleRateCalculator.calculateHz(sampleTimestampsMs = samples), HZ_TOLERANCE)
    }

    @Test
    fun fifty_samples_one_second_yield_49_hz_so_intervals_per_second_formula_holds_at_higher_IMU_rates() {
        // 50 samples = 49 intervals. Span 1000 ms. → 49 Hz.
        // Tests scale at IMU rates (50Hz typical for accelerometer).
        val samples = (0..49).map { (it * 1000L) / 49L }

        assertEquals(49.0, SampleRateCalculator.calculateHz(sampleTimestampsMs = samples), HZ_TOLERANCE)
    }

    @Test
    fun samples_with_zero_span_yield_zero_hz_so_a_batched_emit_at_one_instant_doesnt_render_as_infinity() {
        // Defensive: if a future code path batches multiple samples
        // with the same epoch (sensor delivered them all in one
        // callback), span=0 would produce Infinity. Collapse to 0.
        val samples = listOf(1000L, 1000L, 1000L)

        assertEquals(0.0, SampleRateCalculator.calculateHz(sampleTimestampsMs = samples), HZ_TOLERANCE)
    }

    @Test
    fun degraded_eight_hz_window_reports_eight_hz_so_dashboard_shows_actual_rate_not_configured_target() {
        // 9 samples spanning 1 second = 8 intervals = 8 Hz. A
        // 10Hz-configured pipeline that delivers 9 samples in the
        // last second has degraded; the indicator must show 8 Hz
        // not 10 Hz, so the driver sees the actual cadence.
        val samples = (0..8).map { (it * 1000L) / 8L }

        assertEquals(8.0, SampleRateCalculator.calculateHz(sampleTimestampsMs = samples), HZ_TOLERANCE)
    }
}
