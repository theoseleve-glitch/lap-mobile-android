package com.lap.mobile.capture

/**
 * First Android API level at which [ACCESS_BACKGROUND_LOCATION][LocationPermissionManager.Companion.BACKGROUND_LOCATION]
 * exists as a runtime permission (Q / API 29). Pre-Q devices treat
 * background access as implicit when foreground is granted.
 */
private const val ANDROID_Q_API: Int = 29

/**
 * Pure permission-state machine for the capture pipeline. Asks
 * [PermissionChecker] for the OS-side answer and folds in the device
 * SDK level so callers get one truthful answer to "what should I
 * request next?".
 *
 * The actual Activity-Result-Contract dialog lives in the Compose
 * layer (T087 preflight) — this manager only describes the request
 * payload, never executes it.
 *
 * Foreground-then-background is enforced in [nextPermissionsToRequest]
 * because Android 11+ rejects bundled requests that include
 * `BACKGROUND_LOCATION` alongside fine/coarse — the OS contract is a
 * two-step flow, and on API 30+ the second step has to be a Settings
 * deep-link.
 */
class LocationPermissionManager(
    private val checker: PermissionChecker,
    private val sdkInt: Int,
) {
    fun currentState(): LocationPermissionState {
        val foreground = checker.isGranted(FINE_LOCATION) && checker.isGranted(COARSE_LOCATION)
        val background = if (sdkInt >= ANDROID_Q_API) {
            checker.isGranted(BACKGROUND_LOCATION)
        } else {
            // Pre-Q: BACKGROUND_LOCATION didn't exist; foreground access was always-on.
            foreground
        }
        return LocationPermissionState(
            foregroundGranted = foreground,
            backgroundGranted = background,
        )
    }

    fun nextPermissionsToRequest(): List<String> {
        val state = currentState()
        return when {
            !state.foregroundGranted -> listOf(FINE_LOCATION, COARSE_LOCATION)
            !state.backgroundGranted && sdkInt >= ANDROID_Q_API -> listOf(BACKGROUND_LOCATION)
            else -> emptyList()
        }
    }

    companion object {
        const val FINE_LOCATION: String = "android.permission.ACCESS_FINE_LOCATION"
        const val COARSE_LOCATION: String = "android.permission.ACCESS_COARSE_LOCATION"
        const val BACKGROUND_LOCATION: String = "android.permission.ACCESS_BACKGROUND_LOCATION"
    }
}
