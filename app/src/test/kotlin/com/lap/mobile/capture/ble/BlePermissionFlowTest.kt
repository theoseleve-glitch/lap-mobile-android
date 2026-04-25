package com.lap.mobile.capture.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SDK_TIRAMISU: Int = 33
private const val SDK_ANDROID_S: Int = 31
private const val SDK_ANDROID_R: Int = 30
private const val SDK_ANDROID_Q: Int = 29

class BlePermissionFlowTest {
    @Test
    fun requires_scan_and_connect_on_android_s_and_above() {
        val perms = BlePermissionFlow.requiredPermissions(SDK_ANDROID_S)

        assertEquals(
            listOf(BlePermissionFlow.BLUETOOTH_SCAN, BlePermissionFlow.BLUETOOTH_CONNECT),
            perms,
        )
    }

    @Test
    fun requires_scan_and_connect_on_tiramisu() {
        val perms = BlePermissionFlow.requiredPermissions(SDK_TIRAMISU)

        assertEquals(
            listOf(BlePermissionFlow.BLUETOOTH_SCAN, BlePermissionFlow.BLUETOOTH_CONNECT),
            perms,
        )
    }

    @Test
    fun requires_legacy_bluetooth_plus_fine_location_below_android_s() {
        val perms = BlePermissionFlow.requiredPermissions(SDK_ANDROID_R)

        assertEquals(
            listOf(
                BlePermissionFlow.BLUETOOTH_LEGACY,
                BlePermissionFlow.BLUETOOTH_ADMIN_LEGACY,
                BlePermissionFlow.ACCESS_FINE_LOCATION,
            ),
            perms,
        )
    }

    @Test
    fun requires_legacy_set_on_android_q() {
        val perms = BlePermissionFlow.requiredPermissions(SDK_ANDROID_Q)

        assertTrue(BlePermissionFlow.BLUETOOTH_LEGACY in perms)
        assertTrue(BlePermissionFlow.ACCESS_FINE_LOCATION in perms)
    }

    @Test
    fun decide_returns_granted_when_all_permissions_granted() {
        val granted = mapOf(
            BlePermissionFlow.BLUETOOTH_SCAN to true,
            BlePermissionFlow.BLUETOOTH_CONNECT to true,
        )

        val result = BlePermissionFlow.decide(
            grantResults = granted,
            canShowRationale = { false },
            sdkInt = SDK_ANDROID_S,
        )

        assertEquals(BlePermissionResult.Granted, result)
    }

    @Test
    fun decide_returns_show_rationale_when_denied_with_rationale_available() {
        val granted = mapOf(
            BlePermissionFlow.BLUETOOTH_SCAN to false,
            BlePermissionFlow.BLUETOOTH_CONNECT to true,
        )

        val result = BlePermissionFlow.decide(
            grantResults = granted,
            canShowRationale = { it == BlePermissionFlow.BLUETOOTH_SCAN },
            sdkInt = SDK_ANDROID_S,
        )

        assertEquals(BlePermissionResult.ShowRationale(listOf(BlePermissionFlow.BLUETOOTH_SCAN)), result)
    }

    @Test
    fun decide_returns_open_settings_when_denied_without_rationale() {
        // shouldShowRequestPermissionRationale=false after a denial == user ticked
        // "Don't ask again" → the only remediation is the app-details settings screen.
        val granted = mapOf(
            BlePermissionFlow.BLUETOOTH_SCAN to false,
            BlePermissionFlow.BLUETOOTH_CONNECT to false,
        )

        val result = BlePermissionFlow.decide(
            grantResults = granted,
            canShowRationale = { false },
            sdkInt = SDK_ANDROID_S,
        )

        assertEquals(
            BlePermissionResult.OpenSettings(
                listOf(BlePermissionFlow.BLUETOOTH_SCAN, BlePermissionFlow.BLUETOOTH_CONNECT),
            ),
            result,
        )
    }

    @Test
    fun decide_treats_missing_grant_entry_as_denied() {
        // ActivityResult contracts only return entries for permissions that were asked;
        // the absence of an entry must not be interpreted as "granted".
        val result = BlePermissionFlow.decide(
            grantResults = emptyMap(),
            canShowRationale = { false },
            sdkInt = SDK_ANDROID_S,
        )

        assertTrue(result is BlePermissionResult.OpenSettings)
    }
}
