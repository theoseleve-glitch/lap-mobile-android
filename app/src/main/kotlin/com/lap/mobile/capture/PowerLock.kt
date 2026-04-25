package com.lap.mobile.capture

/**
 * Minimal abstraction over Android's `PowerManager.WakeLock` (T081)
 * so the lifecycle policy ([RecordingPowerGuard]) can be JVM-tested
 * without needing a `Context`. The Android implementation
 * ([AndroidPowerLock]) wraps a `PARTIAL_WAKE_LOCK` returned by
 * `PowerManager.newWakeLock`; the foreground service builds it once
 * and hands it to the guard.
 *
 * `acquire` and `release` must be safe to call multiple times in any
 * order — the guard checks [isHeld] before each call but defensive
 * impls (the real `WakeLock` with `setReferenceCounted(false)`)
 * tolerate it natively.
 */
interface PowerLock {
    val isHeld: Boolean

    fun acquire()

    fun release()
}
