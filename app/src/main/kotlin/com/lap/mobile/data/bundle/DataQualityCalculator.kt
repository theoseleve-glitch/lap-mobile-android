package com.lap.mobile.data.bundle

import com.lap.schemas.v1.DataQuality

private const val MAX_SCORE: Double = 100.0
private const val OVERLAY_MIN_SCORE: Double = 60.0
private const val LONG_TERM_MIN_SCORE: Double = 80.0
private const val RATE_WARNING_THRESHOLD: Double = 0.9
private const val GAP_WARNING_THRESHOLD: Int = 5
private const val FULL_STABILITY: Double = 1.0

/**
 * Inputs to [DataQualityCalculator.compute] (T140). Aggregated from
 * [SessionCoordinator]'s capture telemetry — totalSamples and
 * durationSeconds are the bookend values; expectedRateHz is what the
 * sensor stack was *configured* to produce; sampleGapsCount is what
 * the journal observed at runtime.
 *
 * gpsFixQuality / gpsSatelliteCountAvg propagate to the manifest if
 * the GNSS subsystem reported them — both stay null on phone-only
 * sessions where the platform doesn't expose a fix-quality estimator.
 */
data class CaptureQualityMetrics(
    val totalSamples: Int,
    val durationSeconds: Double,
    val expectedRateHz: Double,
    val sampleGapsCount: Int,
    val gpsFixQuality: Double? = null,
    val gpsSatelliteCountAvg: Int? = null,
)

/**
 * Pure scorer that emits the per-session [DataQuality] verdict the
 * bundle manifest carries (T140). Two derivations:
 *
 *  - **sampleRateStability** = actualRate / expectedRate, clamped to
 *    [0, 1]. A session that hit its target rate scores 1.0; a session
 *    at half-rate scores 0.5.
 *  - **score** = 100 × sampleRateStability. Gaps don't currently
 *    deduct from score (only surface a warning) — they're a
 *    qualitative signal the dashboard renders, not a quantitative
 *    one yet. T140's bar is "computed"; tightening the formula is a
 *    follow-up once we have a few sessions to calibrate against.
 *
 * Usability gates: ≥60 enables overlays (the dashboard's basic
 * comparison views), ≥80 enables long-term coaching (where stale
 * patterns get baked into the recommendation engine, so the bar is
 * stricter). Below 60 the session is informational only.
 *
 * Defensive against zero duration — a finalize attempt before any
 * samples landed must not divide by zero. Caller (BundleFinalizer)
 * surfaces an empty-session outcome separately so the manifest still
 * carries a valid DataQuality row.
 */
object DataQualityCalculator {
    const val WARNING_RATE_BELOW_TARGET: String = "rate_below_target"
    const val WARNING_FREQUENT_GAPS: String = "frequent_gaps"

    fun compute(metrics: CaptureQualityMetrics): DataQuality {
        val actualRate = if (metrics.durationSeconds > 0.0) {
            metrics.totalSamples / metrics.durationSeconds
        } else {
            0.0
        }
        val stability = if (metrics.expectedRateHz > 0.0) {
            (actualRate / metrics.expectedRateHz).coerceIn(0.0, FULL_STABILITY)
        } else {
            0.0
        }
        val score = MAX_SCORE * stability
        val warnings = buildList {
            if (stability < RATE_WARNING_THRESHOLD) add(WARNING_RATE_BELOW_TARGET)
            if (metrics.sampleGapsCount > GAP_WARNING_THRESHOLD) add(WARNING_FREQUENT_GAPS)
        }
        return DataQuality(
            score = score,
            channelCompleteness = MAX_SCORE * stability,
            sampleGapsCount = metrics.sampleGapsCount,
            sampleRateStability = stability,
            usableForOverlays = score >= OVERLAY_MIN_SCORE,
            usableForLongTermCoaching = score >= LONG_TERM_MIN_SCORE,
            gpsFixQuality = metrics.gpsFixQuality,
            gpsSatelliteCountAvg = metrics.gpsSatelliteCountAvg,
            warnings = warnings,
        )
    }
}
