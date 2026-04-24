package com.lap.mobile.capture

import com.lap.mobile.capture.LocationPermissionManager.Companion.BACKGROUND_LOCATION
import com.lap.mobile.capture.LocationPermissionManager.Companion.COARSE_LOCATION
import com.lap.mobile.capture.LocationPermissionManager.Companion.FINE_LOCATION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ANDROID_9_API: Int = 28
private const val ANDROID_10_API: Int = 29
private const val ANDROID_14_API: Int = 34

/**
 * Permission state machine for the foreground (fine+coarse) +
 * background scopes the capture pipeline needs (T061 FLPC at 10 Hz +
 * T063 foreground service that keeps capturing while backgrounded).
 *
 * The manager itself is pure: it asks the [PermissionChecker] for the
 * Android-side answer and folds in the SDK level. That's enough to
 * enumerate "what should I request next?" — the actual Activity
 * Result Contract that pops the dialog lives in the Compose layer
 * (T087 preflight) and isn't this manager's concern.
 */
class LocationPermissionManagerTest {
    @Test
    fun all_permissions_granted_yields_ready_state() {
        val mgr = LocationPermissionManager(
            checker = FakeChecker(setOf(FINE_LOCATION, COARSE_LOCATION, BACKGROUND_LOCATION)),
            sdkInt = ANDROID_14_API,
        )
        val state = mgr.currentState()
        assertTrue(state.foregroundGranted)
        assertTrue(state.backgroundGranted)
        assertTrue(state.readyForCapture)
    }

    @Test
    fun nothing_granted_yields_not_ready() {
        val mgr = LocationPermissionManager(FakeChecker(emptySet()), ANDROID_14_API)
        val state = mgr.currentState()
        assertFalse(state.foregroundGranted)
        assertFalse(state.backgroundGranted)
        assertFalse(state.readyForCapture)
    }

    @Test
    fun nothing_granted_requests_foreground_pair_first() {
        val mgr = LocationPermissionManager(FakeChecker(emptySet()), ANDROID_14_API)
        // Both fine and coarse: FLPC's HIGH_ACCURACY mode needs FINE; we still
        // request COARSE so the user can downgrade gracefully if they deny FINE.
        assertEquals(
            listOf(FINE_LOCATION, COARSE_LOCATION),
            mgr.nextPermissionsToRequest(),
        )
    }

    @Test
    fun only_fine_granted_still_requests_pair() {
        val mgr = LocationPermissionManager(
            FakeChecker(setOf(FINE_LOCATION)),
            ANDROID_14_API,
        )
        assertFalse(mgr.currentState().foregroundGranted)
        assertEquals(
            listOf(FINE_LOCATION, COARSE_LOCATION),
            mgr.nextPermissionsToRequest(),
        )
    }

    @Test
    fun only_coarse_granted_still_requests_pair() {
        val mgr = LocationPermissionManager(
            FakeChecker(setOf(COARSE_LOCATION)),
            ANDROID_14_API,
        )
        assertFalse(mgr.currentState().foregroundGranted)
        assertEquals(
            listOf(FINE_LOCATION, COARSE_LOCATION),
            mgr.nextPermissionsToRequest(),
        )
    }

    @Test
    fun foreground_granted_but_background_missing_requests_background_only() {
        val mgr = LocationPermissionManager(
            FakeChecker(setOf(FINE_LOCATION, COARSE_LOCATION)),
            ANDROID_10_API,
        )
        // Android 11+ can't bundle BACKGROUND with foreground anyway — the
        // OS would reject it. Two-step is the correct contract.
        assertEquals(
            listOf(BACKGROUND_LOCATION),
            mgr.nextPermissionsToRequest(),
        )
    }

    @Test
    fun pre_api_29_treats_background_as_implicit_when_foreground_granted() {
        val mgr = LocationPermissionManager(
            FakeChecker(setOf(FINE_LOCATION, COARSE_LOCATION)),
            ANDROID_9_API,
        )
        // ACCESS_BACKGROUND_LOCATION didn't exist before Android 10 — apps
        // with foreground location had implicit always-on access.
        assertTrue(mgr.currentState().backgroundGranted)
        assertTrue(mgr.currentState().readyForCapture)
        assertEquals(emptyList<String>(), mgr.nextPermissionsToRequest())
    }

    @Test
    fun pre_api_29_without_foreground_does_not_request_background() {
        val mgr = LocationPermissionManager(FakeChecker(emptySet()), ANDROID_9_API)
        // Even though background is "implicit," we still gate on foreground
        // first. No point requesting BACKGROUND_LOCATION on a device whose
        // OS doesn't recognize it.
        assertEquals(
            listOf(FINE_LOCATION, COARSE_LOCATION),
            mgr.nextPermissionsToRequest(),
        )
    }

    @Test
    fun all_granted_returns_empty_request_list() {
        val mgr = LocationPermissionManager(
            FakeChecker(setOf(FINE_LOCATION, COARSE_LOCATION, BACKGROUND_LOCATION)),
            ANDROID_14_API,
        )
        assertEquals(emptyList<String>(), mgr.nextPermissionsToRequest())
    }
}

/**
 * Test [PermissionChecker] backed by a literal granted-set. Real
 * Android impl wraps `ContextCompat.checkSelfPermission`.
 */
private class FakeChecker(private val granted: Set<String>) : PermissionChecker {
    override fun isGranted(permission: String): Boolean = permission in granted
}
