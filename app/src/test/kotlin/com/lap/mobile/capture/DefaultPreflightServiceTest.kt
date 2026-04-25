package com.lap.mobile.capture

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val LOCATION_PERMISSION_REASON: String = "location_permission"
private const val GPS_ACCURACY_REASON: String = "gps_accuracy"
private const val BLUETOOTH_REASON: String = "bluetooth"
private const val EXPECTED_TWO_REASONS: Int = 2

/**
 * Unit tests for [DefaultPreflightService] (T086). Each check is a
 * single function returning `null`-on-pass / `String`-on-fail; the
 * service aggregates all failure reasons into a [PreflightStatus]. A
 * single failure flips the result to [PreflightStatus.NotReady] and
 * the UI (T097) renders every reason — partial readiness is never
 * surfaced as Ready.
 */
class DefaultPreflightServiceTest {
    @Test
    fun no_checks_means_ready() = runTest {
        val service = DefaultPreflightService(checks = emptyList())

        val status = service.check()

        assertEquals(PreflightStatus.Ready, status)
    }

    @Test
    fun all_checks_passing_returns_ready() = runTest {
        val service = DefaultPreflightService(
            checks = listOf(
                PreflightCheck { null },
                PreflightCheck { null },
                PreflightCheck { null },
            ),
        )

        val status = service.check()

        assertEquals(PreflightStatus.Ready, status)
    }

    @Test
    fun single_failing_check_returns_not_ready_with_that_reason() = runTest {
        val service = DefaultPreflightService(
            checks = listOf(
                PreflightCheck { null },
                PreflightCheck { LOCATION_PERMISSION_REASON },
                PreflightCheck { null },
            ),
        )

        val status = service.check()

        assertTrue("expected NotReady, got $status", status is PreflightStatus.NotReady)
        val reasons = (status as PreflightStatus.NotReady).reasons
        assertEquals(listOf(LOCATION_PERMISSION_REASON), reasons)
    }

    @Test
    fun multiple_failing_checks_aggregate_all_reasons_in_check_order() = runTest {
        val service = DefaultPreflightService(
            checks = listOf(
                PreflightCheck { LOCATION_PERMISSION_REASON },
                PreflightCheck { null },
                PreflightCheck { GPS_ACCURACY_REASON },
                PreflightCheck { BLUETOOTH_REASON },
            ),
        )

        val status = service.check()

        assertTrue("expected NotReady, got $status", status is PreflightStatus.NotReady)
        val reasons = (status as PreflightStatus.NotReady).reasons
        assertEquals(EXPECTED_TWO_REASONS + 1, reasons.size)
        assertEquals(
            listOf(LOCATION_PERMISSION_REASON, GPS_ACCURACY_REASON, BLUETOOTH_REASON),
            reasons,
        )
    }

    @Test
    fun preflight_service_is_a_preflight_gate() = runTest {
        val service: PreflightService = DefaultPreflightService(checks = emptyList())

        // Up-cast to the narrower SessionCoordinator-facing surface.
        val gate: PreflightGate = service

        assertEquals(PreflightStatus.Ready, gate.check())
    }
}
