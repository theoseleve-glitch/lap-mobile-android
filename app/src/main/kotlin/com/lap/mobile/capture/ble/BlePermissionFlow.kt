package com.lap.mobile.capture.ble

private const val ANDROID_API_S: Int = 31

/**
 * Outcome of [BlePermissionFlow.decide] (T121). The UI maps each
 * variant to a different next step:
 *
 *  - [Granted] — proceed with the BLE scan / connect.
 *  - [ShowRationale] — show an in-app explanation then re-request via
 *    `ActivityResultContracts.RequestMultiplePermissions()`. The
 *    permissions list carries which ones still need granting so the
 *    rationale text can be specific.
 *  - [OpenSettings] — the user has tapped "Don't ask again" on at
 *    least one denial; the only remediation is the app-details
 *    settings screen ([com.lap.mobile.capture.PreflightDeepLink] maps
 *    `bluetooth_permission` to `APPLICATION_DETAILS_SETTINGS`).
 */
sealed interface BlePermissionResult {
    data object Granted : BlePermissionResult
    data class ShowRationale(val permissions: List<String>) : BlePermissionResult
    data class OpenSettings(val permissions: List<String>) : BlePermissionResult
}

/**
 * Pure-Kotlin BLE permission flow kernel (T121). Encapsulates two
 * decisions the UI layer needs but shouldn't reimplement per
 * Activity/Composable:
 *
 *  1. **Which permissions to ask for**, given the device's SDK level
 *     ([requiredPermissions]). Android 12 (S, API 31+) split the
 *     install-time `BLUETOOTH` / `BLUETOOTH_ADMIN` into runtime
 *     `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT`. Pre-S devices still
 *     need the legacy permissions plus `ACCESS_FINE_LOCATION` —
 *     before the SCAN permission existed, location-fine was the gate
 *     for any BLE scan to return results.
 *  2. **What to do after the request returns** ([decide]). Map the
 *     grant results + per-permission `shouldShowRequestPermissionRationale`
 *     to a [BlePermissionResult] the UI can act on. The classic
 *     "permanently denied" detection: if the request just returned
 *     denied AND the system says "don't show rationale", the user
 *     ticked "Don't ask again" — only the app-details settings
 *     screen unblocks them.
 *
 * Keeping this kernel pure means the policy is JVM-tested without
 * `ActivityResultContracts` / `Manifest` constants; the caller wires
 * in `Build.VERSION.SDK_INT` and the `shouldShowRequestPermissionRationale`
 * lookup at the boundary.
 */
object BlePermissionFlow {
    const val BLUETOOTH_SCAN: String = "android.permission.BLUETOOTH_SCAN"
    const val BLUETOOTH_CONNECT: String = "android.permission.BLUETOOTH_CONNECT"
    const val BLUETOOTH_LEGACY: String = "android.permission.BLUETOOTH"
    const val BLUETOOTH_ADMIN_LEGACY: String = "android.permission.BLUETOOTH_ADMIN"
    const val ACCESS_FINE_LOCATION: String = "android.permission.ACCESS_FINE_LOCATION"

    fun requiredPermissions(sdkInt: Int): List<String> = if (sdkInt >= ANDROID_API_S) {
        listOf(BLUETOOTH_SCAN, BLUETOOTH_CONNECT)
    } else {
        listOf(BLUETOOTH_LEGACY, BLUETOOTH_ADMIN_LEGACY, ACCESS_FINE_LOCATION)
    }

    fun decide(
        grantResults: Map<String, Boolean>,
        canShowRationale: (String) -> Boolean,
        sdkInt: Int,
    ): BlePermissionResult {
        // ActivityResult contracts only return entries for permissions that were asked;
        // an absent entry is treated as denied — never as granted-by-omission.
        val missing = requiredPermissions(sdkInt).filter { grantResults[it] != true }
        if (missing.isEmpty()) return BlePermissionResult.Granted
        return if (missing.any(canShowRationale)) {
            BlePermissionResult.ShowRationale(missing.filter(canShowRationale))
        } else {
            BlePermissionResult.OpenSettings(missing)
        }
    }
}
