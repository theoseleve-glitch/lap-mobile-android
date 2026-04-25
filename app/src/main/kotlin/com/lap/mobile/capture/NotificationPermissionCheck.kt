package com.lap.mobile.capture

private const val ANDROID_API_TIRAMISU: Int = 33

/**
 * Preflight check for `POST_NOTIFICATIONS` (T088). Android 13
 * (Tiramisu, API 33) introduced this as a runtime permission. The
 * foreground service (T063) is FGS-contract-required to display a
 * persistent notification while recording — without
 * `POST_NOTIFICATIONS`, the system will refuse to surface it and
 * the FGS will be killed shortly after `startForeground` per Google's
 * Android 13 FGS guidance — so this is a hard preflight block.
 *
 * Pre-T devices have no equivalent permission and are always granted.
 * The [sdkInt] gate is explicit so the check is JVM-testable across
 * both branches without needing to manipulate `Build.VERSION.SDK_INT`.
 */
class NotificationPermissionCheck(
    private val checker: PermissionChecker,
    private val sdkInt: Int,
) : PreflightCheck {
    override suspend fun failureReason(): String? {
        if (sdkInt < ANDROID_API_TIRAMISU) return null
        return if (checker.isGranted(POST_NOTIFICATIONS)) null else REASON_NOTIFICATION_PERMISSION
    }

    companion object {
        const val REASON_NOTIFICATION_PERMISSION: String = "notification_permission"
        const val POST_NOTIFICATIONS: String = "android.permission.POST_NOTIFICATIONS"
    }
}
