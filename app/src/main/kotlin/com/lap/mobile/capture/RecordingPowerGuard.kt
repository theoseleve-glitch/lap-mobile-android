package com.lap.mobile.capture

import com.lap.mobile.data.RecordingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Holds a [PowerLock] (PARTIAL_WAKE_LOCK on Android) for the exact
 * duration of a recording (T081). Subscribes to a [RecordingState]
 * flow:
 *
 *  - state == [RecordingState.RECORDING] && !isHeld → [PowerLock.acquire]
 *  - state != [RecordingState.RECORDING] && isHeld → [PowerLock.release]
 *
 * The `isHeld` guards keep both directions idempotent — duplicate
 * emissions, defensive impls, and out-of-band Wake state changes can
 * never produce double-acquire or double-release.
 *
 * The collector runs inside a `try { … } finally { release if held }`
 * so cancellation (foreground service teardown, scope cancellation,
 * exception propagation) never leaks an OS-level wake lock.
 */
class RecordingPowerGuard(
    private val powerLock: PowerLock,
    private val state: Flow<RecordingState>,
) {
    fun start(scope: CoroutineScope): Job = scope.launch {
        try {
            state.collect { current ->
                val shouldHold = current == RecordingState.RECORDING
                when {
                    shouldHold && !powerLock.isHeld -> powerLock.acquire()
                    !shouldHold && powerLock.isHeld -> powerLock.release()
                }
            }
        } finally {
            if (powerLock.isHeld) powerLock.release()
        }
    }
}
