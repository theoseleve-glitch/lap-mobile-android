package com.lap.mobile.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the contract for [SpeedZoneClassifier] (T193) — the pure
 * function that turns a speed (km/h) into a [SpeedZone] color band
 * for the [com.lap.mobile.ui.dashboard.SpeedGauge].
 *
 * Why a kernel split (classifier separate from the Compose gauge):
 * "color-coded" is a UX claim that depends on a stable mapping
 * between speed and zone. JVM tests lock that mapping; the gauge
 * Composable just renders the resolved zone. A future tweak to the
 * thresholds (e.g., a track-class-aware override for karting vs
 * GP-circuit) can change the kernel without touching the gauge.
 *
 * Thresholds are the v1 default for circuit racing in km/h:
 *   - <50 km/h  → Cool    (pit lane, paddock, slowest corners)
 *   - 50–99     → Cruise  (warmup laps, slow sections)
 *   - 100–149   → Fast    (active racing, most straights)
 *   - ≥150 km/h → Redline (top-speed straights)
 *
 * Boundaries are strict-less-than at the upper edge of each band so
 * a speed of exactly 50 lands in Cruise (not Cool). The convention
 * matches how drivers read speedometers — "I crossed 100" means the
 * Fast zone has just engaged.
 *
 * Negative speeds defensively collapse to Cool — GPS speed is never
 * negative in practice (CompositeSpeedFilter clamps), but if a future
 * data path ever emitted a -0.5 from a NaN-ish accelerometer drift,
 * the gauge must not crash or render an invalid zone.
 */
class SpeedZoneClassifierTest {
    @Test
    fun zero_kmh_is_cool_so_pit_lane_idle_renders_in_calm_color() {
        assertEquals(SpeedZone.Cool, SpeedZoneClassifier.classify(speedKmh = 0.0))
    }

    @Test
    fun forty_nine_kmh_is_cool_so_just_below_threshold_doesnt_jump_into_cruise() {
        assertEquals(SpeedZone.Cool, SpeedZoneClassifier.classify(speedKmh = 49.999))
    }

    @Test
    fun fifty_kmh_exactly_is_cruise_so_threshold_belongs_to_higher_band_per_drivers_mental_model() {
        // Drivers say "I crossed 50" to mean the next band engaged —
        // the boundary belongs to the higher band, not the lower one.
        assertEquals(SpeedZone.Cruise, SpeedZoneClassifier.classify(speedKmh = 50.0))
    }

    @Test
    fun ninety_nine_kmh_is_cruise_so_just_below_one_hundred_doesnt_jump_into_fast() {
        assertEquals(SpeedZone.Cruise, SpeedZoneClassifier.classify(speedKmh = 99.999))
    }

    @Test
    fun one_hundred_kmh_exactly_is_fast_so_active_racing_band_engages_at_the_landmark() {
        assertEquals(SpeedZone.Fast, SpeedZoneClassifier.classify(speedKmh = 100.0))
    }

    @Test
    fun one_forty_nine_kmh_is_fast_so_just_below_redline_doesnt_jump_into_critical_color() {
        assertEquals(SpeedZone.Fast, SpeedZoneClassifier.classify(speedKmh = 149.999))
    }

    @Test
    fun one_fifty_kmh_exactly_is_redline_so_top_speed_band_engages_at_the_landmark() {
        assertEquals(SpeedZone.Redline, SpeedZoneClassifier.classify(speedKmh = 150.0))
    }

    @Test
    fun three_hundred_kmh_is_still_redline_so_no_band_above_redline_exists_yet() {
        // No "ultra-redline" yet — nothing in v1 needs to distinguish
        // 200 km/h from 280 km/h visually. If a future track type
        // (top-fuel drag, F1) wants more bands, add them here without
        // changing the gauge.
        assertEquals(SpeedZone.Redline, SpeedZoneClassifier.classify(speedKmh = 300.0))
    }

    @Test
    fun negative_speed_collapses_to_cool_so_a_corrupted_data_path_doesnt_crash_the_gauge() {
        // Defensive: GPS speed is never negative. If a future filter
        // emits -0.5 from NaN-ish drift, the gauge must render
        // something safe — Cool is the only band that doesn't lie
        // about activity.
        assertEquals(SpeedZone.Cool, SpeedZoneClassifier.classify(speedKmh = -1.0))
    }
}
