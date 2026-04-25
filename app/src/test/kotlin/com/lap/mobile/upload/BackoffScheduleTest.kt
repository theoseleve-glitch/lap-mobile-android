package com.lap.mobile.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val SAMPLE_COUNT_FOR_JITTER_BOUNDS: Int = 1_000

class BackoffScheduleTest {
    @Test
    fun attempt_one_starts_at_one_second_base() {
        val delay = BackoffSchedule.delayForAttempt(attempt = 1, random = ZERO_JITTER_RANDOM)
        assertEquals(1.seconds, delay)
    }

    @Test
    fun attempt_two_doubles_to_two_seconds_base() {
        val delay = BackoffSchedule.delayForAttempt(attempt = 2, random = ZERO_JITTER_RANDOM)
        assertEquals(2.seconds, delay)
    }

    @Test
    fun attempt_three_doubles_to_four_seconds_base() {
        val delay = BackoffSchedule.delayForAttempt(attempt = 3, random = ZERO_JITTER_RANDOM)
        assertEquals(4.seconds, delay)
    }

    @Test
    fun attempt_four_doubles_to_eight_seconds_base() {
        val delay = BackoffSchedule.delayForAttempt(attempt = 4, random = ZERO_JITTER_RANDOM)
        assertEquals(8.seconds, delay)
    }

    @Test
    fun attempt_five_doubles_to_sixteen_seconds_base() {
        val delay = BackoffSchedule.delayForAttempt(attempt = 5, random = ZERO_JITTER_RANDOM)
        assertEquals(16.seconds, delay)
    }

    @Test
    fun attempt_six_caps_to_thirty_seconds_base_breaking_the_pure_doubling() {
        // Pure doubling would land at 32s; the schedule pins to 30s here so
        // the curve plateaus at a known minute boundary by attempt 7.
        val delay = BackoffSchedule.delayForAttempt(attempt = 6, random = ZERO_JITTER_RANDOM)
        assertEquals(30.seconds, delay)
    }

    @Test
    fun attempt_seven_reaches_sixty_seconds_base() {
        val delay = BackoffSchedule.delayForAttempt(attempt = 7, random = ZERO_JITTER_RANDOM)
        assertEquals(60.seconds, delay)
    }

    @Test
    fun attempts_beyond_seven_stay_pinned_at_sixty_seconds_base() {
        // T155 caps retries at 10 before failed_permanent, but the schedule
        // itself must not overflow if a future change raises that bound.
        val tail = BackoffSchedule.delayForAttempt(attempt = 100, random = ZERO_JITTER_RANDOM)
        assertEquals(60.seconds, tail)
    }

    @Test
    fun attempt_zero_throws_so_a_caller_does_not_use_the_schedule_for_a_first_try() {
        assertThrows(IllegalArgumentException::class.java) {
            BackoffSchedule.delayForAttempt(attempt = 0, random = ZERO_JITTER_RANDOM)
        }
    }

    @Test
    fun negative_attempt_throws_so_a_corrupted_counter_surfaces_loudly() {
        assertThrows(IllegalArgumentException::class.java) {
            BackoffSchedule.delayForAttempt(attempt = -1, random = ZERO_JITTER_RANDOM)
        }
    }

    @Test
    fun jitter_never_drops_delay_below_the_base_so_the_back_pressure_floor_holds() {
        // Across many seeds, the delay for attempt 4 (base = 8s) must
        // never come back shorter than 8s — additive jitter guarantees
        // we never retry sooner than the doubling curve says.
        for (seed in 0 until SAMPLE_COUNT_FOR_JITTER_BOUNDS) {
            val delay = BackoffSchedule.delayForAttempt(attempt = 4, random = Random(seed.toLong()))
            assertTrue(
                "seed $seed produced $delay < 8s — jitter must add, not subtract",
                delay >= 8.seconds,
            )
        }
    }

    @Test
    fun jitter_caps_at_twenty_five_percent_above_base_for_a_predictable_upper_bound() {
        // Same attempt, same range check on the upper side: never exceed
        // base + 25% so the worst-case delay between attempts is bounded.
        val maxAllowed = (8_000 + 8_000 / 4).milliseconds
        for (seed in 0 until SAMPLE_COUNT_FOR_JITTER_BOUNDS) {
            val delay = BackoffSchedule.delayForAttempt(attempt = 4, random = Random(seed.toLong()))
            assertTrue(
                "seed $seed produced $delay > $maxAllowed — jitter ceiling violated",
                delay <= maxAllowed,
            )
        }
    }

    @Test
    fun jitter_actually_varies_so_two_clients_do_not_thunder_the_server_in_lockstep() {
        // Sample many seeds, count distinct delays. With ±0..2000ms jitter
        // on an 8s base we expect well over a hundred distinct values out
        // of 1000 seeds — assert at least 50 to leave generous slack.
        val distinct = (0 until SAMPLE_COUNT_FOR_JITTER_BOUNDS)
            .map { BackoffSchedule.delayForAttempt(attempt = 4, random = Random(it.toLong())) }
            .distinct()
            .size
        assertTrue("only $distinct distinct delays in 1000 seeds — jitter too narrow", distinct >= 50)
    }
}

/**
 * A deterministic [Random] that always returns the lower bound of any
 * range — i.e., zero jitter. Lets the curve-shape tests assert the
 * exact base delay without having to reason about the RNG.
 */
private val ZERO_JITTER_RANDOM: Random = object : Random() {
    override fun nextBits(bitCount: Int): Int = 0
    override fun nextLong(from: Long, until: Long): Long = from
    override fun nextLong(until: Long): Long = 0L
    override fun nextLong(): Long = 0L
}
