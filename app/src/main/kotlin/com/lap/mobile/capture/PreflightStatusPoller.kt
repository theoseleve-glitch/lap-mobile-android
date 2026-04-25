package com.lap.mobile.capture

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.time.Duration

internal const val DEFAULT_PREFLIGHT_POLL_INTERVAL_SECONDS: Long = 2L

/**
 * Polls a [PreflightService] every [pollInterval] and emits the
 * latest [PreflightStatus] as a cold [Flow] (T099). The first
 * emission fires immediately so the readiness UI doesn't show a
 * stale empty state during the first interval; subsequent
 * emissions follow the configured cadence.
 *
 * Cold flow (rather than a [kotlinx.coroutines.flow.StateFlow]) so
 * polling stops the moment the consumer cancels — there's no
 * background lifecycle to leak. The `isActive` guard inside the
 * emitter loop is belt-and-suspenders against scope cancellation
 * during the `service.check()` call itself.
 *
 * 2 s default matches the granularity at which permission grants /
 * GPS fixes / battery percentage actually change — finer would just
 * burn battery on the foreground service for no UI win.
 */
class PreflightStatusPoller(
    private val service: PreflightService,
    private val pollInterval: Duration = Duration.ofSeconds(DEFAULT_PREFLIGHT_POLL_INTERVAL_SECONDS),
) {
    fun observe(): Flow<PreflightStatus> = flow {
        while (currentCoroutineContext().isActive) {
            emit(service.check())
            delay(pollInterval.toMillis())
        }
    }
}
