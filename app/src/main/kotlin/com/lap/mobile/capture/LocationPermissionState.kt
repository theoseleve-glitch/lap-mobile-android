package com.lap.mobile.capture

/**
 * Snapshot of the location-permission scopes the capture pipeline
 * cares about. [foregroundGranted] gates T061's FLPC stream;
 * [backgroundGranted] gates T063's foreground service that keeps
 * capturing while the app is backgrounded. Both must be true before
 * recording can start (T077 preflight gate).
 */
data class LocationPermissionState(
    val foregroundGranted: Boolean,
    val backgroundGranted: Boolean,
) {
    val readyForCapture: Boolean
        get() = foregroundGranted && backgroundGranted
}
