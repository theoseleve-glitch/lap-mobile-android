package com.lap.mobile.capture

/**
 * Preflight readiness check for location permissions (T087). Hard-
 * blocks recording if foreground (`ACCESS_FINE_LOCATION` /
 * `ACCESS_COARSE_LOCATION`) is missing, or — on Android 10+ (Q, API
 * 29+) — if background (`ACCESS_BACKGROUND_LOCATION`) is missing
 * while the foreground service runs in the background.
 *
 * Returns **at most one** failure reason per call. Foreground takes
 * priority over background because the OS itself runs a two-step
 * permission flow on Q+ — the user must grant foreground before
 * Android will even surface the background prompt — so guiding the
 * UI to the foreground request first is what actually unblocks the
 * user. Surfacing both reasons at once would just produce a redundant
 * "background denied" card the user can't act on.
 *
 * Pre-Q (API 28-) gets implicit background access whenever foreground
 * is granted; [LocationPermissionManager] handles the API gate so
 * this check just consults `currentState()`.
 */
class LocationPermissionCheck(
    private val permissionManager: LocationPermissionManager,
) : PreflightCheck {
    override suspend fun failureReason(): String? {
        val state = permissionManager.currentState()
        return when {
            !state.foregroundGranted -> REASON_LOCATION_PERMISSION
            !state.backgroundGranted -> REASON_BACKGROUND_LOCATION_PERMISSION
            else -> null
        }
    }

    companion object {
        const val REASON_LOCATION_PERMISSION: String = "location_permission"
        const val REASON_BACKGROUND_LOCATION_PERMISSION: String = "background_location_permission"
    }
}
