package com.lap.mobile.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the contract for [BatteryStatusClassifier] (T201) — the
 * pure function that decides whether the battery percentage
 * crosses the warning threshold for the dashboard's
 * [com.lap.mobile.ui.dashboard.BatteryIndicator].
 *
 * Two-band classification (not three): the acceptance is simply
 * "warning at <20%". Drivers care about the binary "do I need to
 * think about charging soon?" — adding a yellow intermediate
 * (e.g., 20-40% as "Low") would over-warn and dilute the signal.
 * If a 5% "critical" red-shout is ever wanted, the classifier
 * grows a new band; for v1 the binary is the truth.
 *
 * Threshold: strict-less-than 20%. A reading of exactly 20% is
 * Normal (no warning). The convention matches drivers' mental
 * model — "I crossed below 20" means the warning engaged. The
 * boundary belongs to the safer band.
 *
 * Defensive cases:
 *   - 0% or negative percentages collapse to LowWarning (clearly
 *     in the danger zone or sensor reporting nonsense).
 *   - >100% (sensor over-report glitch) treated as Normal — over-
 *     reporting is not the kind of drift that should trigger a
 *     warning.
 *
 * Pure function — no Android, no time, no I/O — so the contract
 * is locked by JVM tests and the Android shell can't accidentally
 * change semantics by changing how it observes BatteryManager.
 */
class BatteryStatusClassifierTest {
    @Test
    fun one_hundred_percent_is_normal_so_a_freshly_charged_phone_doesnt_falsely_warn() {
        assertEquals(BatteryStatus.Normal, BatteryStatusClassifier.classify(percentage = 100))
    }

    @Test
    fun fifty_percent_is_normal_so_mid_charge_is_obviously_safe() {
        assertEquals(BatteryStatus.Normal, BatteryStatusClassifier.classify(percentage = 50))
    }

    @Test
    fun exactly_twenty_percent_is_normal_because_threshold_is_strict_less_than_per_drivers_mental_model() {
        // "Warning at <20%" means 20 itself is not yet a warning.
        // Drivers say "I crossed BELOW 20" to describe engagement
        // — the boundary belongs to the safer band.
        assertEquals(BatteryStatus.Normal, BatteryStatusClassifier.classify(percentage = 20))
    }

    @Test
    fun nineteen_percent_is_low_warning_so_just_below_threshold_engages_warning_per_T201_acceptance() {
        // Headline T201 invariant: warning engages at <20%.
        assertEquals(BatteryStatus.LowWarning, BatteryStatusClassifier.classify(percentage = 19))
    }

    @Test
    fun five_percent_is_low_warning_so_critical_levels_continue_to_warn_no_separate_critical_band_in_v1() {
        // No "Critical" red-shout band in v1 — drivers see one
        // consistent warning from <20% downward. If a future need
        // splits this (e.g., 5% → "stop now"), the classifier
        // grows a band; for v1 the binary is enough.
        assertEquals(BatteryStatus.LowWarning, BatteryStatusClassifier.classify(percentage = 5))
    }

    @Test
    fun zero_percent_is_low_warning_so_a_dying_battery_obviously_warns_not_normal() {
        assertEquals(BatteryStatus.LowWarning, BatteryStatusClassifier.classify(percentage = 0))
    }

    @Test
    fun negative_percentage_collapses_to_low_warning_so_a_glitched_sensor_lands_on_safe_default_alert() {
        // Defensive: BatteryManager occasionally returns -1 to mean
        // "unknown". Treat unknown as warning rather than silently
        // hiding it — surfacing "we don't know your battery, treat
        // as low" is the safer driver-facing default.
        assertEquals(BatteryStatus.LowWarning, BatteryStatusClassifier.classify(percentage = -1))
    }

    @Test
    fun over_one_hundred_percent_is_normal_so_an_over_reporting_sensor_glitch_doesnt_falsely_warn() {
        // Defensive in the OPPOSITE direction: occasionally a
        // sensor calibration produces 102% briefly. That's NOT the
        // kind of drift that should trigger a warning — over-
        // reporting is annoying, not dangerous.
        assertEquals(BatteryStatus.Normal, BatteryStatusClassifier.classify(percentage = 102))
    }
}
