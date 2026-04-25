package com.lap.mobile.capture

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

/**
 * Shared finite [LocationDataSource] for unit tests. Promoted to
 * `internal` so sibling tests in the same package don't collide on
 * `private class FixedLocationDataSource` (same pattern as
 * [FixedClock]).
 *
 * Flow completes after the last sample, so `toList()` on consumers
 * returns deterministically without any subscriber/emitter dance.
 */
internal class FixedLocationDataSource(private val samples: List<LocationSample>) : LocationDataSource {
    override fun samples(): Flow<LocationSample> = samples.asFlow()
}
