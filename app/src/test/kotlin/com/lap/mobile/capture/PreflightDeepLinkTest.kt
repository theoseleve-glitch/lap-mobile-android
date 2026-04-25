package com.lap.mobile.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val APP_PACKAGE: String = "com.lap.mobile"

/**
 * Unit tests for [PreflightDeepLink] (T098). The Compose CTA tier
 * needs to know `which Intent action / target uri` for every
 * blocking reason, and "no deep-link" for in-app remediations
 * (`track_not_selected`, `racebox_not_connected`, `thermal_high`).
 *
 * Permission failures route to the per-app details screen
 * (`APPLICATION_DETAILS_SETTINGS` + `package:` URI) rather than the
 * general permissions screen — Android's per-permission deep-links
 * are SDK-fragmented and inconsistent across OEMs, but the per-app
 * details screen is universally available since API 9 and the user
 * lands one tap away from the permission toggle.
 */
class PreflightDeepLinkTest {
    @Test
    fun location_permission_routes_to_app_details() {
        val spec = PreflightDeepLink.specFor(
            LocationPermissionCheck.REASON_LOCATION_PERMISSION,
            APP_PACKAGE,
        )

        assertEquals(PreflightDeepLink.ACTION_APP_DETAILS_SETTINGS, spec?.action)
        assertEquals("${PreflightDeepLink.URI_PACKAGE_PREFIX}$APP_PACKAGE", spec?.uriString)
    }

    @Test
    fun background_location_permission_routes_to_app_details() {
        val spec = PreflightDeepLink.specFor(
            LocationPermissionCheck.REASON_BACKGROUND_LOCATION_PERMISSION,
            APP_PACKAGE,
        )

        assertEquals(PreflightDeepLink.ACTION_APP_DETAILS_SETTINGS, spec?.action)
    }

    @Test
    fun notification_permission_routes_to_app_details() {
        val spec = PreflightDeepLink.specFor(
            NotificationPermissionCheck.REASON_NOTIFICATION_PERMISSION,
            APP_PACKAGE,
        )

        assertEquals(PreflightDeepLink.ACTION_APP_DETAILS_SETTINGS, spec?.action)
    }

    @Test
    fun bluetooth_permission_routes_to_app_details() {
        val spec = PreflightDeepLink.specFor(
            BluetoothCheck.REASON_BLUETOOTH_PERMISSION,
            APP_PACKAGE,
        )

        assertEquals(PreflightDeepLink.ACTION_APP_DETAILS_SETTINGS, spec?.action)
    }

    @Test
    fun bluetooth_disabled_routes_to_bluetooth_settings() {
        val spec = PreflightDeepLink.specFor(
            BluetoothCheck.REASON_BLUETOOTH_DISABLED,
            APP_PACKAGE,
        )

        assertEquals(PreflightDeepLink.ACTION_BLUETOOTH_SETTINGS, spec?.action)
        assertNull("BT settings is global — no package URI", spec?.uriString)
    }

    @Test
    fun gps_failures_route_to_location_settings() {
        for (reason in listOf(
            GpsFixQualityCheck.REASON_GPS_NO_FIX,
            GpsFixQualityCheck.REASON_GPS_ACCURACY,
            GpsFixQualityCheck.REASON_GPS_SATELLITES,
        )) {
            val spec = PreflightDeepLink.specFor(reason, APP_PACKAGE)
            assertEquals(
                "$reason should route to location settings",
                PreflightDeepLink.ACTION_LOCATION_SOURCE_SETTINGS,
                spec?.action,
            )
        }
    }

    @Test
    fun storage_low_routes_to_internal_storage_settings() {
        val spec = PreflightDeepLink.specFor(
            StorageAvailabilityCheck.REASON_STORAGE_LOW,
            APP_PACKAGE,
        )

        assertEquals(PreflightDeepLink.ACTION_INTERNAL_STORAGE_SETTINGS, spec?.action)
    }

    @Test
    fun battery_low_routes_to_battery_saver_settings() {
        val spec = PreflightDeepLink.specFor(
            BatteryLevelCheck.REASON_BATTERY_LOW,
            APP_PACKAGE,
        )

        assertEquals(PreflightDeepLink.ACTION_BATTERY_SAVER_SETTINGS, spec?.action)
    }

    @Test
    fun in_app_reasons_have_no_deep_link() {
        for (reason in listOf(
            TrackSelectionCheck.REASON_TRACK_NOT_SELECTED,
            RaceboxConnectionCheck.REASON_RACEBOX_NOT_CONNECTED,
            ThermalStateCheck.REASON_THERMAL_HIGH,
        )) {
            assertNull(
                "$reason is in-app or wait-only — no settings deep-link",
                PreflightDeepLink.specFor(reason, APP_PACKAGE),
            )
        }
    }

    @Test
    fun unknown_reason_returns_null() {
        assertNull(PreflightDeepLink.specFor("future_unknown_reason", APP_PACKAGE))
    }
}
