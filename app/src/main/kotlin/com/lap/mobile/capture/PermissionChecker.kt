package com.lap.mobile.capture

/**
 * Provider-agnostic "is this permission granted?" check. Wraps the
 * Android-only `ContextCompat.checkSelfPermission` ([ContextPermissionChecker])
 * so [LocationPermissionManager] stays JVM-testable without Robolectric.
 */
fun interface PermissionChecker {
    fun isGranted(permission: String): Boolean
}
