package com.lap.mobile.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * `IMPORTANCE_LOW` per
 * [android.app.NotificationManager.IMPORTANCE_LOW] — silent, no sound.
 * Defined as a literal in [CaptureNotificationTest] (and in the
 * production [CaptureNotification]) so JVM tests can lock the policy
 * without an Android runtime.
 */
private const val IMPORTANCE_LOW: Int = 2

/**
 * Constants lock for the persistent-notification config T063 wires
 * into [CaptureForegroundService]. Changing any of these is a behavior
 * change the tests force a deliberate decision on:
 *
 *  - CHANNEL_ID/NOTIFICATION_ID flips lose user-visible notification
 *    state across reinstalls.
 *  - importance flip from LOW would start beeping the user every time
 *    they background the app during a recording.
 */
class CaptureNotificationTest {
    @Test
    fun channel_id_is_stable() {
        // Reinstalls don't reset settings unless this string changes — keep it stable.
        assertEquals("capture_foreground", CaptureNotification.CHANNEL_ID)
    }

    @Test
    fun notification_id_is_nonzero() {
        // 0 is a valid id but conventionally treated as "none" — pick a
        // namespaced value so other notifications don't collide.
        assertNotEquals(0, CaptureNotification.NOTIFICATION_ID)
    }

    @Test
    fun importance_is_low_so_recording_does_not_beep() {
        assertEquals(IMPORTANCE_LOW, CaptureNotification.IMPORTANCE)
    }
}
