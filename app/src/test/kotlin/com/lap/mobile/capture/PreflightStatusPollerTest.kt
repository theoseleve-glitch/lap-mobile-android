package com.lap.mobile.capture

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

private const val POLL_INTERVAL_MS: Long = 2_000L
private const val SIX_SECONDS_MS: Long = 6_000L
private const val EXPECTED_FIRST_EMIT_COUNT: Int = 1
private const val EXPECTED_AFTER_TWO_INTERVALS_COUNT: Int = 3

/**
 * Unit tests for [PreflightStatusPoller] (T099). The recording screen
 * needs the live readiness state without waiting for the user to tap
 * "refresh" — a 2-second poll is the spec'd cadence and matches the
 * granularity at which permission grants / GPS fixes / battery
 * percentage actually change.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PreflightStatusPollerTest {
    @Test
    fun first_emission_fires_immediately_without_waiting() = runTest {
        val service = PreflightService { PreflightStatus.Ready }
        val poller = PreflightStatusPoller(service)
        val collected = mutableListOf<PreflightStatus>()
        val job = launch { poller.observe().toList(collected) }

        runCurrent()

        assertEquals(EXPECTED_FIRST_EMIT_COUNT, collected.size)
        assertEquals(PreflightStatus.Ready, collected[0])
        job.cancelAndJoin()
    }

    @Test
    fun polls_at_specified_interval() = runTest {
        val service = PreflightService { PreflightStatus.Ready }
        val poller = PreflightStatusPoller(service, pollInterval = Duration.ofMillis(POLL_INTERVAL_MS))
        val collected = mutableListOf<PreflightStatus>()
        val job = launch { poller.observe().toList(collected) }

        runCurrent()
        advanceTimeBy(SIX_SECONDS_MS)
        runCurrent()

        // t=0 (initial) + t=2 + t=4 + t=6 = 4 emissions, but
        // advanceTimeBy fires the t=6 delay too — accept 3 or 4.
        assertTrue(
            "expected ≥ 3 emissions over 6s of virtual time, got ${collected.size}",
            collected.size >= EXPECTED_AFTER_TWO_INTERVALS_COUNT,
        )
        job.cancelAndJoin()
    }

    @Test
    fun emits_changing_status_as_service_state_evolves() = runTest {
        var snapshot: PreflightStatus = PreflightStatus.Ready
        val service = PreflightService { snapshot }
        val poller = PreflightStatusPoller(service, pollInterval = Duration.ofMillis(POLL_INTERVAL_MS))
        val collected = mutableListOf<PreflightStatus>()
        val job = launch { poller.observe().toList(collected) }

        runCurrent()
        // First emit was Ready
        snapshot = PreflightStatus.NotReady(reasons = listOf("gps_no_fix"))
        advanceTimeBy(POLL_INTERVAL_MS)
        runCurrent()

        assertTrue(collected.size >= 2)
        assertEquals(PreflightStatus.Ready, collected[0])
        assertEquals(
            PreflightStatus.NotReady(reasons = listOf("gps_no_fix")),
            collected[1],
        )
        job.cancelAndJoin()
    }

    @Test
    fun cancellation_stops_emissions() = runTest {
        var checkInvocations = 0
        val service = PreflightService {
            checkInvocations++
            PreflightStatus.Ready
        }
        val poller = PreflightStatusPoller(service, pollInterval = Duration.ofMillis(POLL_INTERVAL_MS))
        val job = launch { poller.observe().toList(mutableListOf<PreflightStatus>()) }

        runCurrent()
        val invocationsAtCancel = checkInvocations
        job.cancelAndJoin()

        // No more polling after cancel even if we advance time.
        advanceTimeBy(POLL_INTERVAL_MS * 5)
        runCurrent()

        assertEquals(
            "service must not be called after cancellation",
            invocationsAtCancel,
            checkInvocations,
        )
    }
}
