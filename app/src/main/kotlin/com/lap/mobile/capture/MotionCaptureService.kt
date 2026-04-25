package com.lap.mobile.capture

import kotlinx.coroutines.flow.Flow

/**
 * Public motion-capture API for the recording pipeline (T076 wires
 * this into `SessionCoordinator`). Like [LocationCaptureService], the
 * service is a thin pass-through so the platform-specific bits —
 * SensorManager registration, listener teardown, looper handling —
 * stay in [SensorManagerMotionDataSource] where instrumented tests
 * can exercise them on a real device (T083).
 *
 * Each call to [samples] returns a fresh [Flow] of [MotionSample]
 * carrying both [MotionSample.Accelerometer] and
 * [MotionSample.Gyroscope] events interleaved in arrival order —
 * collectors do not share state.
 */
class MotionCaptureService(
    private val source: MotionDataSource,
) {
    fun samples(): Flow<MotionSample> = source.samples()
}
