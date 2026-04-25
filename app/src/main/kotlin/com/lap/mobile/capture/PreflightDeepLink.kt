package com.lap.mobile.capture

/**
 * Side-effect-free settings-screen deep-link spec for the
 * Fix-it CTA (T098). Compose maps this to a real `Intent` at render
 * time — the spec stays JVM-testable while the actual
 * `Uri.parse` / `startActivity` glue lives in
 * [com.lap.mobile.ui.preflight.PreflightChecklist].
 *
 * `uriString` is `null` for actions that don't need a target (e.g.,
 * BT settings via `Settings.ACTION_BLUETOOTH_SETTINGS`); `non-null`
 * when the action is package-scoped (e.g., per-app permissions).
 */
data class PreflightIntentSpec(
    val action: String,
    val uriString: String? = null,
)

/**
 * Maps each preflight failure reason to the system-settings screen
 * the user can fix it from (T098). Returns `null` for in-app
 * remediations — `track_not_selected`, `racebox_not_connected`,
 * `thermal_high` (the user just waits) — so the Compose CTA layer
 * knows when to omit the "Fix it" button entirely.
 *
 * Permission failures all route to `APPLICATION_DETAILS_SETTINGS`
 * with a `package:` URI rather than per-permission deep-links —
 * Android's per-permission targets (e.g., `MANAGE_APP_LOCATION_*`)
 * are SDK-fragmented and inconsistent across OEMs, but the per-app
 * details screen is universally available since API 9 and the user
 * lands one tap away from the permission toggle. One safe fallback
 * beats five fragile happy paths.
 */
object PreflightDeepLink {
    fun specFor(reason: String, packageName: String): PreflightIntentSpec? = when (reason) {
        LocationPermissionCheck.REASON_LOCATION_PERMISSION,
        LocationPermissionCheck.REASON_BACKGROUND_LOCATION_PERMISSION,
        NotificationPermissionCheck.REASON_NOTIFICATION_PERMISSION,
        BluetoothCheck.REASON_BLUETOOTH_PERMISSION,
        -> PreflightIntentSpec(
            action = ACTION_APP_DETAILS_SETTINGS,
            uriString = "$URI_PACKAGE_PREFIX$packageName",
        )

        BluetoothCheck.REASON_BLUETOOTH_DISABLED -> PreflightIntentSpec(action = ACTION_BLUETOOTH_SETTINGS)

        GpsFixQualityCheck.REASON_GPS_NO_FIX,
        GpsFixQualityCheck.REASON_GPS_ACCURACY,
        GpsFixQualityCheck.REASON_GPS_SATELLITES,
        -> PreflightIntentSpec(action = ACTION_LOCATION_SOURCE_SETTINGS)

        StorageAvailabilityCheck.REASON_STORAGE_LOW ->
            PreflightIntentSpec(action = ACTION_INTERNAL_STORAGE_SETTINGS)

        BatteryLevelCheck.REASON_BATTERY_LOW ->
            PreflightIntentSpec(action = ACTION_BATTERY_SAVER_SETTINGS)

        else -> null
    }

    const val ACTION_APP_DETAILS_SETTINGS: String = "android.settings.APPLICATION_DETAILS_SETTINGS"
    const val ACTION_BLUETOOTH_SETTINGS: String = "android.settings.BLUETOOTH_SETTINGS"
    const val ACTION_LOCATION_SOURCE_SETTINGS: String = "android.settings.LOCATION_SOURCE_SETTINGS"
    const val ACTION_INTERNAL_STORAGE_SETTINGS: String = "android.settings.INTERNAL_STORAGE_SETTINGS"
    const val ACTION_BATTERY_SAVER_SETTINGS: String = "android.settings.BATTERY_SAVER_SETTINGS"

    const val URI_PACKAGE_PREFIX: String = "package:"
}
