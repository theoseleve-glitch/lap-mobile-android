package com.lap.mobile.capture

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val ANDROID_API_R: Int = 30
private const val ANDROID_API_S: Int = 31
private const val ANDROID_API_TIRAMISU: Int = 33

/**
 * Unit tests for [BluetoothCheck] (T089). The check only runs when
 * RaceBox mode is enabled — in iRacing-only or pure-mobile-GNSS
 * sessions, BT denial isn't a blocker. Within RaceBox mode it
 * enforces both the runtime `BLUETOOTH_CONNECT` permission (Android
 * 12 / S, API 31+) and the live state of the BT adapter.
 */
class BluetoothCheckTest {
    @Test
    fun racebox_disabled_returns_null_regardless_of_bluetooth_state() = runTest {
        val checker = PermissionChecker { false }
        val adapter = BluetoothAdapterStatus { false }
        val check = BluetoothCheck(
            checker = checker,
            adapter = adapter,
            sdkInt = ANDROID_API_TIRAMISU,
            raceboxModeEnabled = { false },
        )

        assertNull("BT not used when RaceBox is off", check.failureReason())
    }

    @Test
    fun racebox_enabled_with_everything_ok_returns_null() = runTest {
        val checker = PermissionChecker { permission ->
            permission == BluetoothCheck.BLUETOOTH_CONNECT
        }
        val adapter = BluetoothAdapterStatus { true }
        val check = BluetoothCheck(
            checker = checker,
            adapter = adapter,
            sdkInt = ANDROID_API_TIRAMISU,
            raceboxModeEnabled = { true },
        )

        assertNull(check.failureReason())
    }

    @Test
    fun racebox_enabled_s_plus_with_denied_permission_returns_permission_reason() = runTest {
        val checker = PermissionChecker { false }
        val adapter = BluetoothAdapterStatus { true }
        val check = BluetoothCheck(
            checker = checker,
            adapter = adapter,
            sdkInt = ANDROID_API_S,
            raceboxModeEnabled = { true },
        )

        assertEquals(BluetoothCheck.REASON_BLUETOOTH_PERMISSION, check.failureReason())
    }

    @Test
    fun racebox_enabled_with_disabled_adapter_returns_adapter_reason() = runTest {
        val checker = PermissionChecker { permission ->
            permission == BluetoothCheck.BLUETOOTH_CONNECT
        }
        val adapter = BluetoothAdapterStatus { false }
        val check = BluetoothCheck(
            checker = checker,
            adapter = adapter,
            sdkInt = ANDROID_API_TIRAMISU,
            raceboxModeEnabled = { true },
        )

        assertEquals(BluetoothCheck.REASON_BLUETOOTH_DISABLED, check.failureReason())
    }

    @Test
    fun pre_s_skips_bluetooth_connect_permission_check() = runTest {
        val checker = PermissionChecker { false }
        val adapter = BluetoothAdapterStatus { true }
        val check = BluetoothCheck(
            checker = checker,
            adapter = adapter,
            sdkInt = ANDROID_API_R,
            raceboxModeEnabled = { true },
        )

        assertNull("pre-S devices use legacy BLUETOOTH permission granted at install time", check.failureReason())
    }

    @Test
    fun pre_s_with_disabled_adapter_still_blocks() = runTest {
        val checker = PermissionChecker { true }
        val adapter = BluetoothAdapterStatus { false }
        val check = BluetoothCheck(
            checker = checker,
            adapter = adapter,
            sdkInt = ANDROID_API_R,
            raceboxModeEnabled = { true },
        )

        assertEquals(BluetoothCheck.REASON_BLUETOOTH_DISABLED, check.failureReason())
    }

    @Test
    fun permission_takes_priority_over_disabled_adapter() = runTest {
        val checker = PermissionChecker { false }
        val adapter = BluetoothAdapterStatus { false }
        val check = BluetoothCheck(
            checker = checker,
            adapter = adapter,
            sdkInt = ANDROID_API_S,
            raceboxModeEnabled = { true },
        )

        assertEquals(
            "permission must be granted before the adapter state matters",
            BluetoothCheck.REASON_BLUETOOTH_PERMISSION,
            check.failureReason(),
        )
    }
}
