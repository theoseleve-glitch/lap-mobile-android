package com.lap.mobile.data.bundle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EXPECTED_RATE_HZ: Double = 100.0
private const val DURATION_SEC: Double = 60.0
private const val SAMPLES_PERFECT: Int = 6_000 // 100Hz × 60s
private const val SAMPLES_LOW_RATE: Int = 3_000 // 50% of expected
private const val FLOAT_TOL: Double = 1e-9

class DataQualityCalculatorTest {
    @Test
    fun perfect_session_scores_full_with_no_warnings_and_is_usable_for_long_term() {
        val q = DataQualityCalculator.compute(
            CaptureQualityMetrics(
                totalSamples = SAMPLES_PERFECT,
                durationSeconds = DURATION_SEC,
                expectedRateHz = EXPECTED_RATE_HZ,
                sampleGapsCount = 0,
            ),
        )

        assertEquals(100.0, q.score, FLOAT_TOL)
        assertEquals(1.0, q.sampleRateStability, FLOAT_TOL)
        assertEquals(0, q.sampleGapsCount)
        assertTrue(q.usableForOverlays)
        assertTrue(q.usableForLongTermCoaching)
        assertTrue("perfect session must have no warnings", q.warnings.isEmpty())
    }

    @Test
    fun half_rate_session_reflects_in_stability_and_score_drops_proportionally() {
        val q = DataQualityCalculator.compute(
            CaptureQualityMetrics(
                totalSamples = SAMPLES_LOW_RATE,
                durationSeconds = DURATION_SEC,
                expectedRateHz = EXPECTED_RATE_HZ,
                sampleGapsCount = 0,
            ),
        )

        assertEquals(0.5, q.sampleRateStability, FLOAT_TOL)
        assertEquals(50.0, q.score, FLOAT_TOL)
    }

    @Test
    fun gaps_propagate_into_count_and_warning_above_threshold() {
        val q = DataQualityCalculator.compute(
            CaptureQualityMetrics(
                totalSamples = SAMPLES_PERFECT,
                durationSeconds = DURATION_SEC,
                expectedRateHz = EXPECTED_RATE_HZ,
                sampleGapsCount = 12,
            ),
        )

        assertEquals(12, q.sampleGapsCount)
        assertTrue(
            "frequent gaps must surface a warning, got ${q.warnings}",
            DataQualityCalculator.WARNING_FREQUENT_GAPS in q.warnings,
        )
    }

    @Test
    fun low_rate_warns_and_disqualifies_long_term_coaching() {
        val q = DataQualityCalculator.compute(
            CaptureQualityMetrics(
                totalSamples = SAMPLES_LOW_RATE,
                durationSeconds = DURATION_SEC,
                expectedRateHz = EXPECTED_RATE_HZ,
                sampleGapsCount = 0,
            ),
        )

        assertTrue(
            "low rate must surface a warning, got ${q.warnings}",
            DataQualityCalculator.WARNING_RATE_BELOW_TARGET in q.warnings,
        )
        assertFalse(
            "score=50 must NOT meet long-term coaching threshold (80)",
            q.usableForLongTermCoaching,
        )
    }

    @Test
    fun severe_degradation_disqualifies_overlays_too() {
        // ~17% of expected rate — well below the overlay threshold.
        val q = DataQualityCalculator.compute(
            CaptureQualityMetrics(
                totalSamples = SAMPLES_LOW_RATE / 3,
                durationSeconds = DURATION_SEC,
                expectedRateHz = EXPECTED_RATE_HZ,
                sampleGapsCount = 50,
            ),
        )

        assertFalse("severely-degraded session must NOT be usable for overlays", q.usableForOverlays)
        assertFalse(q.usableForLongTermCoaching)
    }

    @Test
    fun zero_duration_does_not_divide_by_zero() {
        // Defensive: a finalize attempt before any samples landed must
        // not crash the bundle write — caller surfaces a "no samples"
        // outcome separately.
        val q = DataQualityCalculator.compute(
            CaptureQualityMetrics(
                totalSamples = 0,
                durationSeconds = 0.0,
                expectedRateHz = EXPECTED_RATE_HZ,
                sampleGapsCount = 0,
            ),
        )

        assertEquals(0.0, q.sampleRateStability, FLOAT_TOL)
        assertEquals(0.0, q.score, FLOAT_TOL)
    }

    @Test
    fun gps_fix_quality_propagates_when_provided() {
        val q = DataQualityCalculator.compute(
            CaptureQualityMetrics(
                totalSamples = SAMPLES_PERFECT,
                durationSeconds = DURATION_SEC,
                expectedRateHz = EXPECTED_RATE_HZ,
                sampleGapsCount = 0,
                gpsFixQuality = 0.92,
                gpsSatelliteCountAvg = 11,
            ),
        )

        assertEquals(0.92, q.gpsFixQuality!!, FLOAT_TOL)
        assertEquals(11, q.gpsSatelliteCountAvg)
    }
}
