package com.lap.mobile.capture

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest
import java.time.Duration

/**
 * Default stale threshold — 5 s at the 10 Hz capture rate (T061) is
 * roughly 50 missed samples in a row, which the receiver basically
 * never produces in normal operation. Faster than this and a single
 * dropped sample would flap the UI; slower and the user wouldn't get
 * timely feedback that they've lost coverage (e.g., entered a tunnel).
 */
private val DEFAULT_STALE_THRESHOLD: Duration = Duration.ofSeconds(5L)

/**
 * GNSS fix-state observer (T067). Folds a [LocationSample] stream
 * into a [LocationFixState] stream the recording-screen ViewModel
 * exposes via `StateFlow<UiState>` (CLAUDE.md convention).
 *
 * State machine, driven entirely by the input flow + a watchdog
 * delay:
 *
 *  - Initial emission: [LocationFixState.Searching].
 *  - Each sample → [LocationFixState.Acquired]; the watchdog timer
 *    is reset (`transformLatest` cancels the previous lambda).
 *  - Watchdog expires without a new sample → [LocationFixState.Lost].
 *  - A sample arriving after Lost → [LocationFixState.Acquired]
 *    (recovery is a normal transition).
 */
class LocationFixMonitor(
    private val staleThreshold: Duration = DEFAULT_STALE_THRESHOLD,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(samples: Flow<LocationSample>): Flow<LocationFixState> = flow {
        emit(LocationFixState.Searching)
        emitAll(
            samples.transformLatest {
                emit(LocationFixState.Acquired)
                delay(staleThreshold.toMillis())
                emit(LocationFixState.Lost)
            },
        )
    }
}
