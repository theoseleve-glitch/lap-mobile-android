package com.lap.mobile.coaching

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Locks the contract for [CoachingPromptThrottle] (T220) — the
 * global throttle limiting coaching insights to max 1 per
 * cooldown window.
 *
 * Coverage:
 *   - First admit always succeeds (no prior state to throttle
 *     against)
 *   - Second admit within window suppressed (headline T220
 *     invariant: noisy rules can't bombard the driver with cards)
 *   - Admit at exact cooldown boundary admitted (inclusive
 *     boundary policy — "after waiting the cooldown, you're
 *     allowed to fire")
 *   - Admit well after cooldown admitted (window expired,
 *     fresh start)
 *   - Multiple rapid attempts within window all suppressed (state
 *     doesn't reset on suppressed call — only admitted calls
 *     update lastAdmittedAt)
 *   - Sequence A(admit)→B(suppress)→C(admit-after-window) works
 *     correctly (suppressed call doesn't reset the cooldown
 *     timer that the next admit needs to clear)
 *   - cooldownDuration=Duration.ZERO disables the throttle
 *     (every admit succeeds)
 *
 * Why a 30s default rather than e.g. 60s or 10s: matches the
 * desktop's [InsightCooldownFilter] default — long enough that
 * the same coaching beat stops being useful (driver heard it;
 * either fixing it or actively ignoring it for now), short
 * enough that a recurring mistake can re-surface after a corner
 * sequence.
 */
class CoachingPromptThrottleTest {
    @Test
    fun first_call_always_admits_so_cold_start_doesnt_swallow_the_first_insight() {
        val throttle = CoachingPromptThrottle(cooldownDuration = Duration.ofSeconds(30))

        assertTrue(throttle.tryAdmit(NOW))
    }

    @Test
    fun second_call_within_cooldown_window_is_suppressed_so_noisy_rules_dont_bombard_driver() {
        // Headline T220 invariant: the driver's attention budget
        // is bounded — the throttle prevents two cards in rapid
        // succession (which the driver couldn't process anyway).
        val throttle = CoachingPromptThrottle(cooldownDuration = Duration.ofSeconds(30))
        throttle.tryAdmit(NOW)

        val secondAttempt = throttle.tryAdmit(NOW.plusSeconds(15))

        assertFalse(secondAttempt)
    }

    @Test
    fun call_at_exact_cooldown_boundary_is_admitted_so_inclusive_boundary_policy_holds() {
        // Boundary case: exactly 30s after first admit. Inclusive
        // policy ("after waiting the cooldown, you're allowed to
        // fire") is easier to reason about than exclusive
        // ("strictly after the cooldown").
        val throttle = CoachingPromptThrottle(cooldownDuration = Duration.ofSeconds(30))
        throttle.tryAdmit(NOW)

        val boundaryAttempt = throttle.tryAdmit(NOW.plusSeconds(30))

        assertTrue(boundaryAttempt)
    }

    @Test
    fun call_well_after_cooldown_is_admitted_so_expired_window_resets_cleanly() {
        val throttle = CoachingPromptThrottle(cooldownDuration = Duration.ofSeconds(30))
        throttle.tryAdmit(NOW)

        val wellAfter = throttle.tryAdmit(NOW.plusSeconds(120))

        assertTrue(wellAfter)
    }

    @Test
    fun multiple_rapid_attempts_within_window_all_suppressed_so_burst_traffic_is_absorbed() {
        val throttle = CoachingPromptThrottle(cooldownDuration = Duration.ofSeconds(30))
        throttle.tryAdmit(NOW)

        // 5 attempts within the 30s window after the first admit.
        repeat(5) { offset ->
            val nthAttempt = throttle.tryAdmit(NOW.plusSeconds((offset + 1).toLong()))
            assertFalse("attempt at +${offset + 1}s should be suppressed", nthAttempt)
        }
    }

    @Test
    fun suppressed_call_does_not_reset_cooldown_timer_so_admit_after_window_works_correctly() {
        // Headline T220 state-machine invariant: only ADMITTED
        // calls update lastAdmittedAt. A suppressed call mid-window
        // doesn't extend the cooldown — the next-admit boundary is
        // still measured from the previous successful admit.
        val throttle = CoachingPromptThrottle(cooldownDuration = Duration.ofSeconds(30))
        throttle.tryAdmit(NOW)
        // Suppressed mid-window — must NOT reset the timer.
        throttle.tryAdmit(NOW.plusSeconds(10))

        // Boundary still measured from the original admit at NOW,
        // so 30s later (NOW + 30) admits cleanly.
        val cleanlyAdmittedAfterWindow = throttle.tryAdmit(NOW.plusSeconds(30))

        assertTrue(cleanlyAdmittedAfterWindow)
    }

    @Test
    fun zero_cooldown_disables_throttle_so_off_setting_admits_everything() {
        val throttle = CoachingPromptThrottle(cooldownDuration = Duration.ZERO)

        repeat(10) { offset ->
            assertTrue(
                "attempt at +${offset}ms should be admitted with zero cooldown",
                throttle.tryAdmit(NOW.plusMillis(offset.toLong())),
            )
        }
    }

    @Test
    fun second_admit_after_window_then_third_within_new_window_is_suppressed_so_chained_admits_throttle_correctly() {
        // Three-event timeline locks the full state-machine cycle:
        //   1. Admit at NOW              → admitted (cold start)
        //   2. Admit at NOW+30           → admitted (window expired)
        //   3. Admit at NOW+45           → suppressed (within new
        //      cooldown window starting at the second admit)
        val throttle = CoachingPromptThrottle(cooldownDuration = Duration.ofSeconds(30))

        assertTrue("first admit", throttle.tryAdmit(NOW))
        assertTrue("second admit at boundary", throttle.tryAdmit(NOW.plusSeconds(30)))
        assertFalse(
            "third admit within new window should be suppressed",
            throttle.tryAdmit(NOW.plusSeconds(45)),
        )
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-04-26T12:00:00Z")
    }
}
