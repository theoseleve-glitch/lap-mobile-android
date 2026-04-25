package com.lap.mobile.capture

import kotlinx.coroutines.flow.Flow

/**
 * Provider-agnostic source of [MotionSample]s. The production
 * implementation ([SensorManagerMotionDataSource]) wraps Android's
 * `SensorManager` for accelerometer + gyroscope; tests inject a
 * fake. This seam is what lets [MotionCaptureService] stay JVM-
 * testable without Robolectric.
 */
fun interface MotionDataSource {
    fun samples(): Flow<MotionSample>
}
