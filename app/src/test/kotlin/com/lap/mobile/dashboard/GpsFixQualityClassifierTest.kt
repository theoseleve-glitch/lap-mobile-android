package com.lap.mobile.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

private const val FIX_TYPE_NONE: Int = 0
private const val FIX_TYPE_DEAD_RECKONING: Int = 1
private const val FIX_TYPE_2D: Int = 2
private const val FIX_TYPE_3D: Int = 3
private const val FIX_TYPE_GNSS_PLUS_DR: Int = 4

private const val GOOD_SATELLITES: Int = 8
private const val MARGINAL_SATELLITES: Int = 4
private const val GOOD_ACCURACY_MM: Long = 2_500L
private const val MARGINAL_ACCURACY_MM: Long = 8_000L
private const val ATROCIOUS_ACCURACY_MM: Long = 30_000L

/**
 * Locks T198 contract — classify the GPS fix into one of three
 * dot colors so the dashboard's [com.lap.mobile.ui.dashboard.GpsFixQualityIndicator]
 * tells the driver at a glance whether to trust the live data.
 *
 * Why three bands (not just good/bad): a 2D fix or a 3D fix with
 * 3-meter accuracy still produces *some* live data — it's just less
 * reliable for cornering analysis (the dashboard's dominant use
 * case). A red-when-degraded indicator would over-warn; a yellow
 * intermediate state matches the actual nuance.
 *
 * Thresholds (v1 default, tuned for circuit racing):
 *   - [GpsFixQuality.Good]:    fixType ≥ 3D AND ≥6 satellites AND
 *                              horizontalAccuracy ≤ 5 m
 *   - [GpsFixQuality.Marginal]: fixType ≥ 2D AND not-Good (any 3D
 *                              fix that misses Good thresholds, or
 *                              any 2D fix at all)
 *   - [GpsFixQuality.NoFix]:   fixType < 2D (no fix, dead-reckoning-
 *                              only, time-only)
 *
 * Why these exact thresholds:
 *   - 6 satellites: minimum for trustworthy 3D triangulation +
 *     redundancy against one bad signal. Fewer satellites means
 *     position can jump as a satellite drops in/out of view.
 *   - 5 m horizontal accuracy: a typical track corner is 10-20 m
 *     wide; 5 m is the boundary where the trail breadcrumb still
 *     visually fits the lane. Tighter would be too strict for the
 *     phone GPS class; looser would let the breadcrumb wander
 *     visibly across lanes.
 *
 * Dead-reckoning-only fixType (1) is treated as NoFix because the
 * dashboard cares about *position* truth — DR drifts unbounded
 * without GPS corrections.
 */
class GpsFixQualityClassifierTest {
    @Test
    fun no_fix_means_red_so_a_just_powered_on_phone_with_no_GPS_lock_shows_red_dot_not_yellow_or_green() {
        // Headline T198 invariant: zero satellites + zero accuracy =
        // red. Any other classification would imply we have data we
        // don't.
        val inputs = GpsFixQualityInputs(
            fixType = FIX_TYPE_NONE,
            numSatellites = 0,
            horizontalAccuracyMm = 0L,
        )

        assertEquals(GpsFixQuality.NoFix, GpsFixQualityClassifier.classify(inputs))
    }

    @Test
    fun dead_reckoning_only_means_red_because_DR_drifts_unbounded_without_GPS_corrections() {
        // fixType=1 = dead reckoning ONLY (sensor-fused position
        // continued during a tunnel). The driver should be told
        // the live trail isn't trustworthy — DR drift compounds.
        val inputs = GpsFixQualityInputs(
            fixType = FIX_TYPE_DEAD_RECKONING,
            numSatellites = 0,
            horizontalAccuracyMm = 5_000L,
        )

        assertEquals(GpsFixQuality.NoFix, GpsFixQualityClassifier.classify(inputs))
    }

    @Test
    fun two_d_fix_with_otherwise_good_metrics_means_yellow_so_a_partial_fix_is_visible_but_not_trusted_as_green() {
        // 2D fix lacks altitude. For dashboard's lat/lon needs that's
        // not directly limiting, but a 2D-only-capable fix indicates
        // marginal sky view (e.g., among trees), and HDOP tends to
        // be poor. Yellow signals "data flowing but degraded".
        val inputs = GpsFixQualityInputs(
            fixType = FIX_TYPE_2D,
            numSatellites = GOOD_SATELLITES,
            horizontalAccuracyMm = GOOD_ACCURACY_MM,
        )

        assertEquals(GpsFixQuality.Marginal, GpsFixQualityClassifier.classify(inputs))
    }

    @Test
    fun three_d_fix_with_too_few_satellites_means_yellow_so_a_fragile_fix_doesnt_falsely_show_green() {
        // 3D fix but only 4 sats → position can jump when a
        // satellite drops out of view. Yellow tells the driver
        // "trust the breadcrumb broadly but not corner-by-corner".
        val inputs = GpsFixQualityInputs(
            fixType = FIX_TYPE_3D,
            numSatellites = MARGINAL_SATELLITES,
            horizontalAccuracyMm = GOOD_ACCURACY_MM,
        )

        assertEquals(GpsFixQuality.Marginal, GpsFixQualityClassifier.classify(inputs))
    }

    @Test
    fun three_d_fix_with_marginal_accuracy_means_yellow_so_the_dot_warns_the_breadcrumb_wanders_outside_lane_width() {
        // 3D fix + 8 sats but horizontal accuracy = 8m. Track lane
        // is ~10-20m wide; 8m means breadcrumb may visibly wander
        // outside the actual line. Yellow signals.
        val inputs = GpsFixQualityInputs(
            fixType = FIX_TYPE_3D,
            numSatellites = GOOD_SATELLITES,
            horizontalAccuracyMm = MARGINAL_ACCURACY_MM,
        )

        assertEquals(GpsFixQuality.Marginal, GpsFixQualityClassifier.classify(inputs))
    }

    @Test
    fun three_d_fix_with_atrocious_accuracy_means_yellow_not_red_because_we_still_have_a_fix_just_a_bad_one() {
        // 3D fix but 30m accuracy is unusable for cornering analysis.
        // BUT it's still a fix — the breadcrumb is approximately
        // right. Distinguishing "have data, can't trust it precisely"
        // (yellow) from "have no data" (red) matters for the driver
        // deciding whether to keep capturing.
        val inputs = GpsFixQualityInputs(
            fixType = FIX_TYPE_3D,
            numSatellites = GOOD_SATELLITES,
            horizontalAccuracyMm = ATROCIOUS_ACCURACY_MM,
        )

        assertEquals(GpsFixQuality.Marginal, GpsFixQualityClassifier.classify(inputs))
    }

    @Test
    fun three_d_fix_with_six_satellites_at_exactly_five_meters_means_green_so_threshold_includes_boundary() {
        // Boundary test: 6 sats (= min good), 5000mm = 5m (= max
        // good accuracy). Both edges INCLUSIVE — the rule is "at
        // least 6, at most 5m" so the threshold itself qualifies.
        val inputs = GpsFixQualityInputs(
            fixType = FIX_TYPE_3D,
            numSatellites = 6,
            horizontalAccuracyMm = 5_000L,
        )

        assertEquals(GpsFixQuality.Good, GpsFixQualityClassifier.classify(inputs))
    }

    @Test
    fun three_d_fix_8_sats_2_5m_accuracy_green_so_typical_open_sky_phone_GPS_fix_shows_green() {
        // Typical good open-sky fix on a phone GPS. Anything in
        // this range is "trust the data".
        val inputs = GpsFixQualityInputs(
            fixType = FIX_TYPE_3D,
            numSatellites = GOOD_SATELLITES,
            horizontalAccuracyMm = GOOD_ACCURACY_MM,
        )

        assertEquals(GpsFixQuality.Good, GpsFixQualityClassifier.classify(inputs))
    }

    @Test
    fun gnss_plus_dr_fix_with_good_metrics_means_green_so_sensor_fused_3D_fixes_dont_get_demoted() {
        // fixType=4 (GNSS + dead reckoning) is BETTER than pure 3D
        // (sensor-fused improves stability through brief GPS losses).
        // Treat fixType >= 3 as "good enough type" so the predicate
        // stays simple and future fixType values (e.g., dual-frequency)
        // don't need code changes here.
        val inputs = GpsFixQualityInputs(
            fixType = FIX_TYPE_GNSS_PLUS_DR,
            numSatellites = GOOD_SATELLITES,
            horizontalAccuracyMm = GOOD_ACCURACY_MM,
        )

        assertEquals(GpsFixQuality.Good, GpsFixQualityClassifier.classify(inputs))
    }
}
