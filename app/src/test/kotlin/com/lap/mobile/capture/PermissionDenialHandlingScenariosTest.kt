package com.lap.mobile.capture

import com.lap.mobile.capture.ble.BlePermissionFlow
import com.lap.mobile.capture.ble.BlePermissionResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val APP_PACKAGE: String = "com.lap.mobile"
private const val SDK_API_33: Int = 33
private const val SDK_PRE_TIRAMISU: Int = 32
private const val SDK_ANDROID_S: Int = 31

/**
 * JVM integration tests for permission-denial handling (T230 — the
 * device-required half is marked PENDING in the sprint).
 *
 * Stitches together two surfaces that the per-component unit tests
 * exercise in isolation:
 *
 *   `PermissionChecker(false) → PreflightCheck.failureReason()`
 *     `→ PreflightDeepLink.specFor(reason, package)`
 *
 * The unit tests for [PreflightDeepLink] feed in the *constants*
 * directly (`PreflightDeepLink.specFor(LocationPermissionCheck.REASON_LOCATION_PERMISSION, …)`);
 * they don't actually exercise the path "a denied checker produces a
 * reason that the deep-link knows how to route". If a future refactor
 * renames a `REASON_*` constant on the check side without updating
 * the corresponding `when` arm in [PreflightDeepLink], the unit tests
 * still pass (each side uses the same renamed symbol). This
 * integration test catches that drift by running the full chain.
 *
 * Also exercises the post-denial decision tree from [BlePermissionFlow]
 * (T121) which bridges the gap between "permission was denied" and
 * "what the UI does next" — `ShowRationale` (re-ask via Activity
 * Result Contract) vs `OpenSettings` (the user ticked "Don't ask
 * again" so only the app-details settings screen unblocks them).
 *
 * What this test does NOT cover (genuinely device-required, hence
 * the [PENDING: device-required] mark in the sprint file):
 *  - Real `ActivityResultContracts.RequestMultiplePermissions()` flow.
 *  - Real `Activity.shouldShowRequestPermissionRationale` calls.
 *  - Real `startActivity(Intent(ACTION_APP_DETAILS_SETTINGS, …))` —
 *    whether the OEM-customised settings screen actually opens at
 *    the permission toggle (it does on AOSP; can drift on heavy skins).
 *  - Real `Build.VERSION.SDK_INT` gating across pre-T / T+ devices.
 *
 * What it DOES cover (the denial-handling regression surface):
 *  - Each denial flavour produces the expected `failureReason()`
 *    string.
 *  - That string round-trips through [PreflightDeepLink.specFor] to
 *    the expected remediation [PreflightIntentSpec]. A drift between
 *    the check's `REASON_*` constant and the deep-link's `when`
 *    discriminator breaks here.
 *  - `bluetooth_disabled` (adapter off with permission granted) routes
 *    to BT settings — a *different* remediation surface from the
 *    permission-denied case which routes to app-details.
 *  - [BlePermissionFlow.decide] returns `ShowRationale` when at least
 *    one denied permission can still be re-requested, and
 *    `OpenSettings` only when every denial is permanent — covering the
 *    partial-denial subtlety the per-test cases don't combine.
 */
class PermissionDenialHandlingScenariosTest {
    @Test
    fun denied_foreground_location_surfaces_reason_and_routes_to_app_details_settings_with_package_uri() = runTest {
        val checker = DenialFakeChecker() // all permissions denied
        val manager = LocationPermissionManager(checker = checker, sdkInt = SDK_API_33)
        val check = LocationPermissionCheck(permissionManager = manager)

        val reason = check.failureReason()

        // The denial-handling chain must produce LOCATION_PERMISSION
        // (not BACKGROUND_*) because foreground takes priority — Android
        // surfaces the foreground prompt before the background prompt
        // becomes available.
        assertEquals(LocationPermissionCheck.REASON_LOCATION_PERMISSION, reason)

        val spec = PreflightDeepLink.specFor(reason!!, APP_PACKAGE)
        assertEquals(PreflightDeepLink.ACTION_APP_DETAILS_SETTINGS, spec?.action)
        assertEquals("${PreflightDeepLink.URI_PACKAGE_PREFIX}$APP_PACKAGE", spec?.uriString)
    }

    @Test
    fun denied_background_location_after_foreground_granted_surfaces_background_reason_and_routes_to_app_details() =
        runTest {
            val checker = DenialFakeChecker(
                granted = setOf(
                    LocationPermissionManager.FINE_LOCATION,
                    LocationPermissionManager.COARSE_LOCATION,
                ),
            )
            val manager = LocationPermissionManager(checker = checker, sdkInt = SDK_API_33)
            val check = LocationPermissionCheck(permissionManager = manager)

            val reason = check.failureReason()

            assertEquals(LocationPermissionCheck.REASON_BACKGROUND_LOCATION_PERMISSION, reason)
            // Same APPLICATION_DETAILS_SETTINGS — Android doesn't ship a
            // stable per-permission deep-link for background-location, so
            // the user lands on the per-app screen and toggles from there.
            // The per-permission deep-links that DO exist
            // (e.g. ACTION_MANAGE_APP_PERMISSIONS) are SDK-fragmented.
            val spec = PreflightDeepLink.specFor(reason!!, APP_PACKAGE)
            assertEquals(PreflightDeepLink.ACTION_APP_DETAILS_SETTINGS, spec?.action)
        }

    @Test
    fun denied_notification_permission_on_tiramisu_or_above_surfaces_reason_and_routes_to_app_details() = runTest {
        val checker = DenialFakeChecker()
        val check = NotificationPermissionCheck(checker = checker, sdkInt = SDK_API_33)

        val reason = check.failureReason()

        assertEquals(NotificationPermissionCheck.REASON_NOTIFICATION_PERMISSION, reason)
        val spec = PreflightDeepLink.specFor(reason!!, APP_PACKAGE)
        assertEquals(PreflightDeepLink.ACTION_APP_DETAILS_SETTINGS, spec?.action)
    }

    @Test
    fun pre_tiramisu_devices_pass_notification_check_even_with_permission_denied_so_no_remediation_needed() = runTest {
        // Headline T230 SDK-gating invariant: POST_NOTIFICATIONS is
        // a runtime permission only on API 33+. On older devices the
        // permission doesn't exist; the FGS notification surfaces
        // unconditionally. The check must not block recording on
        // older OS versions.
        val checker = DenialFakeChecker() // every permission denied
        val check = NotificationPermissionCheck(checker = checker, sdkInt = SDK_PRE_TIRAMISU)

        val reason = check.failureReason()

        assertNull("pre-Tiramisu must not block on POST_NOTIFICATIONS", reason)
    }

    @Test
    fun denied_bluetooth_permission_in_racebox_mode_surfaces_permission_reason_and_routes_to_app_details() = runTest {
        // DenialFakeChecker default = empty granted set → BLUETOOTH_CONNECT denied.
        val checker = DenialFakeChecker()
        // Adapter "on" is irrelevant here — the permission gate runs first.
        val check = BluetoothCheck(
            checker = checker,
            adapter = { true },
            sdkInt = SDK_API_33,
            raceboxModeEnabled = { true },
        )

        val reason = check.failureReason()

        // BluetoothCheck prioritises permission over adapter state —
        // remediating the permission must come first because Android
        // 12+ won't even let the app trigger an adapter-enable intent
        // without BLUETOOTH_CONNECT.
        assertEquals(BluetoothCheck.REASON_BLUETOOTH_PERMISSION, reason)
        val spec = PreflightDeepLink.specFor(reason!!, APP_PACKAGE)
        assertEquals(PreflightDeepLink.ACTION_APP_DETAILS_SETTINGS, spec?.action)
        assertEquals("${PreflightDeepLink.URI_PACKAGE_PREFIX}$APP_PACKAGE", spec?.uriString)
    }

    @Test
    fun adapter_off_with_permission_granted_routes_to_bluetooth_settings_not_app_details() = runTest {
        // Different remediation from the permission-denied case: the user
        // doesn't need to grant anything — they need to toggle BT in
        // system settings. APPLICATION_DETAILS_SETTINGS would be the
        // wrong destination (no permission toggle to flip).
        val checker = DenialFakeChecker(granted = setOf(BluetoothCheck.BLUETOOTH_CONNECT))
        val check = BluetoothCheck(
            checker = checker,
            adapter = { false },
            sdkInt = SDK_API_33,
            raceboxModeEnabled = { true },
        )

        val reason = check.failureReason()

        assertEquals(BluetoothCheck.REASON_BLUETOOTH_DISABLED, reason)
        val spec = PreflightDeepLink.specFor(reason!!, APP_PACKAGE)
        assertEquals(PreflightDeepLink.ACTION_BLUETOOTH_SETTINGS, spec?.action)
        assertNull("BT settings is global; no package URI", spec?.uriString)
    }

    @Test
    fun ble_permission_partial_denial_with_rationale_returns_show_rationale_for_those_perms_only() = runTest {
        // Headline T230 partial-denial subtlety: SCAN denied with rationale
        // available, CONNECT denied without rationale (e.g., never asked
        // before so OS defaults rationale to false). Result: ShowRationale
        // listing only the permissions that CAN still be re-asked. The
        // existing per-test cases assert all-or-nothing rationale; this
        // exercises the mixed case the production decide() filters via
        // `missing.filter(canShowRationale)`.
        val granted = mapOf(
            BlePermissionFlow.BLUETOOTH_SCAN to false,
            BlePermissionFlow.BLUETOOTH_CONNECT to false,
        )

        val result = BlePermissionFlow.decide(
            grantResults = granted,
            canShowRationale = { permission -> permission == BlePermissionFlow.BLUETOOTH_SCAN },
            sdkInt = SDK_ANDROID_S,
        )

        assertTrue("expected ShowRationale, got $result", result is BlePermissionResult.ShowRationale)
        val rationale = result as BlePermissionResult.ShowRationale
        // SCAN is the only one in the rationale list; CONNECT was
        // denied-without-rationale (effectively dont-ask-again) and
        // SHOULDN'T be in the re-ask payload — re-asking would just
        // immediately deny again with no UI surface.
        assertEquals(listOf(BlePermissionFlow.BLUETOOTH_SCAN), rationale.permissions)
    }

    @Test
    fun ble_permission_full_denial_without_any_rationale_returns_open_settings_for_dont_ask_again_recovery() = runTest {
        // The user ticked "Don't ask again" on every permission. Only
        // remediation is the app-details settings screen; calling
        // requestPermissions again would silently deny without any UX.
        val granted = mapOf(
            BlePermissionFlow.BLUETOOTH_SCAN to false,
            BlePermissionFlow.BLUETOOTH_CONNECT to false,
        )

        val result = BlePermissionFlow.decide(
            grantResults = granted,
            canShowRationale = { false },
            sdkInt = SDK_ANDROID_S,
        )

        assertTrue("expected OpenSettings, got $result", result is BlePermissionResult.OpenSettings)
        val openSettings = result as BlePermissionResult.OpenSettings
        assertEquals(
            listOf(BlePermissionFlow.BLUETOOTH_SCAN, BlePermissionFlow.BLUETOOTH_CONNECT),
            openSettings.permissions,
        )
        // The OpenSettings remediation routes via PreflightDeepLink.specFor
        // for the BLUETOOTH_PERMISSION reason — same APPLICATION_DETAILS
        // target, validated against the BluetoothCheck reason vocabulary.
        val spec = PreflightDeepLink.specFor(BluetoothCheck.REASON_BLUETOOTH_PERMISSION, APP_PACKAGE)
        assertEquals(PreflightDeepLink.ACTION_APP_DETAILS_SETTINGS, spec?.action)
    }
}

/**
 * Inline fake [PermissionChecker] that returns true only for
 * permissions in the [granted] set. Empty default = "every
 * permission denied", which is the headline denial-handling
 * fixture.
 */
private class DenialFakeChecker(private val granted: Set<String> = emptySet()) : PermissionChecker {
    override fun isGranted(permission: String): Boolean = permission in granted
}
