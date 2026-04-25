package com.lap.mobile.capture

import android.os.PowerManager

/**
 * Android-side [PowerLock] that wraps a `PARTIAL_WAKE_LOCK` returned
 * by [PowerManager.newWakeLock] (T081). Constructed once by the
 * foreground service / Hilt module and handed to
 * [RecordingPowerGuard]; the guard owns the lifecycle, this class
 * only forwards.
 *
 * `setReferenceCounted(false)` is enforced at construction time so
 * acquire / release are idempotent at the OS level too — defence in
 * depth alongside the guard's own `isHeld` checks. The behavior is
 * Android-only; no JVM unit tests exercise this class. Live
 * verification is deferred to T083 (instrumented 60 s recording).
 */
@Suppress("MissingPermission")
class AndroidPowerLock(private val wakeLock: PowerManager.WakeLock) : PowerLock {
    init {
        wakeLock.setReferenceCounted(false)
    }

    override val isHeld: Boolean
        get() = wakeLock.isHeld

    override fun acquire() {
        if (!wakeLock.isHeld) wakeLock.acquire()
    }

    override fun release() {
        if (wakeLock.isHeld) wakeLock.release()
    }
}
