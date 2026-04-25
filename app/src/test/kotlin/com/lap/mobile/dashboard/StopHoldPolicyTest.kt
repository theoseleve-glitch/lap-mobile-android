package com.lap.mobile.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the contract for [StopHoldPolicy] (T203) — the pure
 * functions that decide "did the driver hold the stop button long
 * enough to count as a deliberate stop intent?" and "what fraction
 * of the way to activation is the current hold?".
 *
 * Why a pure-function policy + a Compose component (not one
 * monolithic StopButton): the Compose layer uses Android-coupled
 * APIs (System.currentTimeMillis, pointerInput.tryAwaitRelease) so
 * the threshold and progress arithmetic gets pulled out into a
 * JVM-testable kernel. If the Compose gesture API shifts (which it
 * has — pointerInput's contract changed in 1.5 and 1.6) the
 * threshold contract is locked here and can't accidentally drift.
 *
 * Threshold contract:
 *   - 500ms (0.5s). Below 0.3s → too easy to trigger by accident
 *     (a brushed finger or rest-on-screen would qualify); above
 *     1.0s → punitive, frustrates a driver who actually wants to
 *     stop. 500ms is the established Material/iOS long-press
 *     baseline — drivers' muscle memory already knows it.
 *   - INCLUSIVE — exactly 500ms counts as activated. Boundary
 *     belongs to the "hold succeeded" band so a hold timed
 *     precisely at 500ms doesn't get rejected by clock rounding.
 *
 * Why two-layer safety (hold THEN confirm): the hold filters
 * accidental contact; the confirm filters intentional-but-mistaken
 * hold (e.g., "I meant to tap the lap button"). Two layers ≈
 * vanishingly small accidental-stop probability mid-session.
 *
 * Defensive: negative durations collapse to "not activated" /
 * progress 0.0 (clock-skew or NaN drift must not flip the policy
 * to "yes activated" or render a weird negative progress fill).
 * Same defensive pattern as T194 LapTimeFormatter, T193 SpeedGauge
 * coerceAtLeast(0.0), T202 SessionElapsedFormatter.
 *
 * Progress contract:
 *   - 0ms → 0.0 (no hold yet, ring empty)
 *   - midpoint (250ms) → 0.5 (linear fill matches user's mental
 *     "half way there" model)
 *   - threshold (500ms) → 1.0 (ring full, will activate on release)
 *   - past threshold (1000ms) → 1.0 CLAMPED (don't visually
 *     overgrow — "fuller than full" is meaningless feedback and
 *     would invite ring-overflow rendering bugs)
 */
class StopHoldPolicyTest {
    @Test
    fun zero_ms_hold_does_not_activate_so_an_initial_press_with_zero_duration_does_not_count_as_stop() {
        // Press-down with no measured duration yet. Must NOT count
        // as activated — otherwise the confirm dialog would pop the
        // instant the driver touches the button.
        assertFalse(StopHoldPolicy.wouldActivate(holdDurationMs = 0L))
    }

    @Test
    fun just_below_threshold_499ms_does_not_activate_so_short_taps_dont_pop_confirm_dialog() {
        // 499ms is "I almost held it" — driver let go before
        // intent threshold. Must not activate. Locks the
        // strict-greater-than-or-equal boundary on the safe side.
        assertFalse(StopHoldPolicy.wouldActivate(holdDurationMs = 499L))
    }

    @Test
    fun exactly_500ms_activates_so_threshold_is_inclusive_and_clock_rounding_doesnt_steal_a_valid_hold() {
        // 500ms = exactly the threshold. INCLUSIVE — boundary
        // belongs to the "activated" band so a precisely-timed
        // hold isn't rejected by 1ms of clock rounding.
        assertTrue(StopHoldPolicy.wouldActivate(holdDurationMs = 500L))
    }

    @Test
    fun just_above_threshold_501ms_activates_so_normal_holds_succeed() {
        // 501ms is the typical successful hold — driver intended
        // to hold for ~half a second.
        assertTrue(StopHoldPolicy.wouldActivate(holdDurationMs = 501L))
    }

    @Test
    fun long_hold_1000ms_activates_so_drivers_who_overshoot_still_get_their_stop_intent_recognized() {
        // 1000ms is "held longer than needed" — common when a
        // driver is being deliberate. Must still activate.
        assertTrue(StopHoldPolicy.wouldActivate(holdDurationMs = 1_000L))
    }

    @Test
    fun negative_hold_does_not_activate_so_clock_skew_cant_silently_trigger_a_stop() {
        // Defensive: same rationale as T202 SessionElapsedFormatter
        // and T194 LapTimeFormatter. A future race-clock subtraction
        // yielding -50ms must not flip activation to true.
        assertFalse(StopHoldPolicy.wouldActivate(holdDurationMs = -50L))
    }

    @Test
    fun progress_at_zero_ms_is_zero_so_the_ring_starts_empty_when_the_driver_first_touches_the_button() {
        assertEquals(0.0f, StopHoldPolicy.progress(holdDurationMs = 0L), PROGRESS_TOLERANCE)
    }

    @Test
    fun progress_at_midpoint_250ms_is_zero_point_five_so_linear_fill_matches_drivers_half_way_mental_model() {
        // 250ms / 500ms = 0.5. Linear fill matches user's "half
        // way there" expectation — anything non-linear would
        // confuse perception of remaining time.
        assertEquals(0.5f, StopHoldPolicy.progress(holdDurationMs = 250L), PROGRESS_TOLERANCE)
    }

    @Test
    fun progress_at_threshold_500ms_is_one_so_the_ring_visually_completes_at_the_activation_moment() {
        // Ring reaches full at exact activation moment — the
        // "ring full" visual IS the "stop intent recognized"
        // signal driver feels.
        assertEquals(1.0f, StopHoldPolicy.progress(holdDurationMs = 500L), PROGRESS_TOLERANCE)
    }

    @Test
    fun progress_past_threshold_1000ms_clamps_to_one_so_overhold_doesnt_render_overgrown_ring() {
        // Past-threshold holds (driver kept holding) must NOT
        // render progress > 1.0 — visually meaningless and would
        // either crash or render a clipped ring depending on draw
        // implementation. Clamp at the kernel boundary.
        assertEquals(1.0f, StopHoldPolicy.progress(holdDurationMs = 1_000L), PROGRESS_TOLERANCE)
    }

    @Test
    fun progress_for_negative_hold_is_zero_so_clock_skew_cant_render_negative_ring_arc() {
        // Defensive: same rationale as wouldActivate negative case.
        // Clamp at zero so a negative duration renders as empty
        // ring, not a "negative arc" rendering bug.
        assertEquals(0.0f, StopHoldPolicy.progress(holdDurationMs = -50L), PROGRESS_TOLERANCE)
    }

    private companion object {
        // Float comparisons need a tiny tolerance — 0.5 might come
        // back as 0.49999998 from integer-divide-then-float arithmetic.
        const val PROGRESS_TOLERANCE: Float = 0.0001f
    }
}
