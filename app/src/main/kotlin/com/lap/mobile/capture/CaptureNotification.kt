package com.lap.mobile.capture

/**
 * Persistent-notification config for [CaptureForegroundService] (T063
 * shell, T064 lock-screen visibility). Centralized so policy choices
 * — channel ID, importance, lock-screen visibility, OS category — are
 * locked in tests:
 *
 *  - flipping [IMPORTANCE] off `LOW` would start beeping the user
 *    every time they background the app during a recording.
 *  - flipping [LOCK_SCREEN_VISIBILITY] off `PUBLIC` would hide the
 *    "Recording session" indicator on the lock screen, letting users
 *    accidentally kill the app and lose the session.
 *  - flipping [CATEGORY] off `service` files the notification under
 *    "Miscellaneous" in OS settings, where it can be silenced.
 *
 * Constants are literal Ints/Strings (not the Android `Notification.*`
 * fields) so JVM tests in [CaptureNotificationTest] can lock them
 * without dragging in the Android runtime — the values match the
 * platform constants exactly.
 */
object CaptureNotification {
    const val CHANNEL_ID: String = "capture_foreground"
    const val NOTIFICATION_ID: Int = 4_201

    /** Equals [android.app.NotificationManager.IMPORTANCE_LOW]. */
    const val IMPORTANCE: Int = 2

    /** Equals [android.app.Notification.VISIBILITY_PUBLIC]. */
    const val LOCK_SCREEN_VISIBILITY: Int = 1

    /** Equals [android.app.Notification.CATEGORY_SERVICE]. */
    const val CATEGORY: String = "service"
}
