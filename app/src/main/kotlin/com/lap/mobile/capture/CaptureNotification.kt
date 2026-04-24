package com.lap.mobile.capture

/**
 * Persistent-notification config for [CaptureForegroundService] (T063).
 * Centralized so the channel ID, notification ID, and importance are
 * locked in tests — flipping importance off `LOW` would start beeping
 * the user every time they background the app during a recording, and
 * flipping the channel ID resets per-channel settings on existing
 * installs.
 *
 * [IMPORTANCE] equals [android.app.NotificationManager.IMPORTANCE_LOW];
 * defined as a literal here so the JVM-side test in
 * [CaptureNotificationTest] doesn't drag in the Android runtime.
 */
object CaptureNotification {
    const val CHANNEL_ID: String = "capture_foreground"
    const val NOTIFICATION_ID: Int = 4_201
    const val IMPORTANCE: Int = 2
}
