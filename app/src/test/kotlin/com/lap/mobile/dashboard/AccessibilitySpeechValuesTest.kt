package com.lap.mobile.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the contract for [AccessibilitySpeechValues] (T209) — the
 * pure functions that produce TalkBack-friendly numeric values for
 * accessibility announcements.
 *
 * Why the kernel exists at all (vs. inlining
 * `speedKmh.coerceAtLeast(0.0).roundToInt()` at every call site):
 * the visual readout and the accessibility readout MUST agree.
 * If a sighted user sees "65" on the [com.lap.mobile.ui.dashboard.SpeedGauge]
 * and a blind user hears TalkBack say "66 kilometers per hour",
 * that's a divergence bug invisible to anyone who tests only one
 * accessibility mode at a time. Centralizing the rounding in a
 * pure function means both readouts compute identically — change
 * the rounding once, both stay in sync.
 *
 * Speed-rounding contract:
 *   - Defensive coerce-to-zero before rounding (sensor noise can
 *     produce small negatives during stationary GPS jitter; same
 *     rationale as T193 SpeedGauge.coerceAtLeast(0.0))
 *   - Kotlin's Double.roundToInt() = "ties round towards positive
 *     infinity" — so 0.5 → 1, 65.5 → 66, 99.5 → 100. This is
 *     half-away-from-zero for positives, which matches typical
 *     speedometer display rounding (no driver expects "65.5"
 *     to round down to 65).
 *
 * Pure function — no Android, no time, no I/O — so the rounding
 * contract is locked here and the Compose layer stays a thin
 * presentation surface that just reads this value into a
 * stringResource template.
 */
class AccessibilitySpeechValuesTest {
    @Test
    fun zero_speed_rounds_to_zero_so_stationary_state_announces_zero_not_garbage() {
        // Stationary state must announce "0 kilometers per hour"
        // not "1" (rounded up from sensor jitter) or "-0" (signed
        // weirdness). Anchor case for the contract.
        assertEquals(0, AccessibilitySpeechValues.roundSpeedForAnnouncement(speedKmh = 0.0))
    }

    @Test
    fun fractional_speed_rounds_down_when_below_half_so_65_point_4_announces_as_65() {
        // 65.4 < 65.5 → rounds to 65. Standard "round to nearest"
        // behavior matching driver expectation.
        assertEquals(65, AccessibilitySpeechValues.roundSpeedForAnnouncement(speedKmh = 65.4))
    }

    @Test
    fun fractional_speed_rounds_up_when_above_half_so_65_point_6_announces_as_66() {
        // 65.6 > 65.5 → rounds to 66.
        assertEquals(66, AccessibilitySpeechValues.roundSpeedForAnnouncement(speedKmh = 65.6))
    }

    @Test
    fun exactly_half_rounds_up_per_kotlin_round_to_int_contract_so_99_point_5_announces_as_100() {
        // Headline boundary case — Kotlin's Double.roundToInt()
        // says "ties round towards positive infinity," so 99.5 → 100.
        // Locks the half-away-from-zero contract so a future
        // refactor to BigDecimal HALF_EVEN doesn't silently change
        // the announcement.
        assertEquals(100, AccessibilitySpeechValues.roundSpeedForAnnouncement(speedKmh = 99.5))
    }

    @Test
    fun typical_race_speed_142_point_7_announces_as_143_so_realistic_high_speed_rounds_correctly() {
        // 142.7 → 143. Sanity check at a typical racing speed
        // (well into T193 SpeedZone.Fast band).
        assertEquals(143, AccessibilitySpeechValues.roundSpeedForAnnouncement(speedKmh = 142.7))
    }

    @Test
    fun negative_speed_collapses_to_zero_so_gps_jitter_doesnt_announce_negative_kilometers() {
        // Defensive: stationary GPS jitter can produce small
        // negative speeds. Coerce to 0 BEFORE rounding so the
        // announcement is "0 kilometers per hour" not "-3" which
        // would be confusing and unprofessional. Same defensive
        // pattern as T193 SpeedGauge.coerceAtLeast(0.0).
        assertEquals(0, AccessibilitySpeechValues.roundSpeedForAnnouncement(speedKmh = -3.2))
    }
}
