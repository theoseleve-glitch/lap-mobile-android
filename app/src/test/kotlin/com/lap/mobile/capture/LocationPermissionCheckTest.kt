package com.lap.mobile.capture

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val ANDROID_API_PRE_Q: Int = 28
private const val ANDROID_API_Q: Int = 29
private const val ANDROID_API_TIRAMISU: Int = 33

/**
 * Unit tests for [LocationPermissionCheck] (T087). Drives the check
 * via fake [PermissionChecker] lambdas across both API gates:
 *
 *  - Pre-Q (API 28-): only foreground exists; background is implicit.
 *  - Q+ (API 29+): both foreground AND background must be granted
 *    independently.
 *
 * The check emits **at most one** reason (foreground takes priority);
 * the UI asks for foreground first per the OS's two-step contract,
 * so exposing both reasons at once would be redundant guidance.
 */
class LocationPermissionCheckTest {
    @Test
    fun all_granted_on_q_plus_returns_null() = runTest {
        val checker = PermissionChecker { permission ->
            permission in setOf(
                LocationPermissionManager.FINE_LOCATION,
                LocationPermissionManager.COARSE_LOCATION,
                LocationPermissionManager.BACKGROUND_LOCATION,
            )
        }
        val check = LocationPermissionCheck(LocationPermissionManager(checker, sdkInt = ANDROID_API_TIRAMISU))

        assertNull(check.failureReason())
    }

    @Test
    fun foreground_denied_returns_location_permission_reason() = runTest {
        val checker = PermissionChecker { false }
        val check = LocationPermissionCheck(LocationPermissionManager(checker, sdkInt = ANDROID_API_TIRAMISU))

        assertEquals(LocationPermissionCheck.REASON_LOCATION_PERMISSION, check.failureReason())
    }

    @Test
    fun foreground_granted_background_denied_on_q_plus_returns_background_reason() = runTest {
        val checker = PermissionChecker { permission ->
            permission != LocationPermissionManager.BACKGROUND_LOCATION
        }
        val check = LocationPermissionCheck(LocationPermissionManager(checker, sdkInt = ANDROID_API_Q))

        assertEquals(
            LocationPermissionCheck.REASON_BACKGROUND_LOCATION_PERMISSION,
            check.failureReason(),
        )
    }

    @Test
    fun foreground_granted_on_pre_q_returns_null_without_background_check() = runTest {
        val checker = PermissionChecker { permission ->
            permission in setOf(
                LocationPermissionManager.FINE_LOCATION,
                LocationPermissionManager.COARSE_LOCATION,
            )
            // BACKGROUND_LOCATION not in set — but pre-Q must not be queried for it.
        }
        val check = LocationPermissionCheck(LocationPermissionManager(checker, sdkInt = ANDROID_API_PRE_Q))

        assertNull("pre-Q implicitly grants background when foreground is granted", check.failureReason())
    }

    @Test
    fun foreground_takes_priority_over_background_when_both_missing() = runTest {
        val checker = PermissionChecker { false }
        val check = LocationPermissionCheck(LocationPermissionManager(checker, sdkInt = ANDROID_API_Q))

        assertEquals(
            "foreground reason should surface first — the OS two-step flow asks for foreground first",
            LocationPermissionCheck.REASON_LOCATION_PERMISSION,
            check.failureReason(),
        )
    }
}
