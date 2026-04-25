package com.lap.mobile.capture

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

/**
 * Shared finite [MotionDataSource] for unit tests. Promoted to
 * `internal` so sibling tests in the same package don't collide on
 * `private class FixedMotionDataSource`.
 */
internal class FixedMotionDataSource(private val samples: List<MotionSample>) : MotionDataSource {
    override fun samples(): Flow<MotionSample> = samples.asFlow()
}
