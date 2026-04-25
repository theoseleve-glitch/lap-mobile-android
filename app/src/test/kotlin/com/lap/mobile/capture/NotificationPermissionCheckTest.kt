package com.lap.mobile.capture

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val ANDROID_API_S_V2: Int = 32
private const val ANDROID_API_TIRAMISU: Int = 33
private const val ANDROID_API_UPSIDE_DOWN: Int = 34

/**
 * Unit tests for [NotificationPermissionCheck] (T088). Android 13
 * (Tiramisu, API 33) introduced runtime [POST_NOTIFICATIONS]
 * permission; the foreground service (T063) needs an active
 * notification by FGS contract, so on T+ devices a denied
 * `POST_NOTIFICATIONS` is a hard preflight block. Pre-T devices
 * never had the permission and are always granted.
 */
class NotificationPermissionCheckTest {
    @Test
    fun pre_tiramisu_returns_null_even_when_checker_says_denied() = runTest {
        val checker = PermissionChecker { false }
        val check = NotificationPermissionCheck(checker, sdkInt = ANDROID_API_S_V2)

        assertNull("pre-T devices have no POST_NOTIFICATIONS permission", check.failureReason())
    }

    @Test
    fun tiramisu_with_granted_permission_returns_null() = runTest {
        val checker = PermissionChecker { permission ->
            permission == NotificationPermissionCheck.POST_NOTIFICATIONS
        }
        val check = NotificationPermissionCheck(checker, sdkInt = ANDROID_API_TIRAMISU)

        assertNull(check.failureReason())
    }

    @Test
    fun tiramisu_with_denied_permission_returns_notification_reason() = runTest {
        val checker = PermissionChecker { false }
        val check = NotificationPermissionCheck(checker, sdkInt = ANDROID_API_TIRAMISU)

        assertEquals(
            NotificationPermissionCheck.REASON_NOTIFICATION_PERMISSION,
            check.failureReason(),
        )
    }

    @Test
    fun future_api_with_denied_permission_still_blocks() = runTest {
        val checker = PermissionChecker { false }
        val check = NotificationPermissionCheck(checker, sdkInt = ANDROID_API_UPSIDE_DOWN)

        assertEquals(
            "newer Android versions still require POST_NOTIFICATIONS",
            NotificationPermissionCheck.REASON_NOTIFICATION_PERMISSION,
            check.failureReason(),
        )
    }
}
