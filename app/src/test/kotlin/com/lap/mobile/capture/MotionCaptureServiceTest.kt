package com.lap.mobile.capture

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val ACCEL_X_MPS2: Float = 0.10f
private const val ACCEL_Y_MPS2: Float = 0.20f
private const val ACCEL_Z_MPS2: Float = 9.80f
private const val GYRO_X_RAD_S: Float = 0.001f
private const val GYRO_Y_RAD_S: Float = 0.002f
private const val GYRO_Z_RAD_S: Float = 0.003f
private const val ACCEL_TS_NS: Long = 1_000L
private const val GYRO_TS_NS: Long = 1_500L

/**
 * The capture service is a thin pass-through over [MotionDataSource]
 * — the SensorManager + SensorEventListener + Looper plumbing lives in
 * [SensorManagerMotionDataSource], which needs an Android runtime to
 * test (T083 instrumented). These cases lock the JVM-seam contract:
 *
 *  - emissions reach the collector in order
 *  - both Accelerometer and Gyroscope subtypes pass through unchanged
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MotionCaptureServiceTest {
    @Test
    fun forwards_accelerometer_and_gyroscope_samples_in_order() = runTest {
        val accel: MotionSample = MotionSample.Accelerometer(
            xMps2 = ACCEL_X_MPS2,
            yMps2 = ACCEL_Y_MPS2,
            zMps2 = ACCEL_Z_MPS2,
            sourceTimestampNs = ACCEL_TS_NS,
        )
        val gyro: MotionSample = MotionSample.Gyroscope(
            xRadPerSec = GYRO_X_RAD_S,
            yRadPerSec = GYRO_Y_RAD_S,
            zRadPerSec = GYRO_Z_RAD_S,
            sourceTimestampNs = GYRO_TS_NS,
        )
        val expected = listOf(accel, gyro)
        val service = MotionCaptureService(FixedMotionDataSource(expected))

        val collected = service.samples().toList()

        assertEquals(expected, collected)
    }

    @Test
    fun empty_source_yields_empty_flow() = runTest {
        val service = MotionCaptureService(FixedMotionDataSource(emptyList()))

        val collected = service.samples().toList()

        assertEquals(emptyList<MotionSample>(), collected)
    }
}

/**
 * Deterministic finite [MotionDataSource] for service tests — Flow
 * completes after the last sample, so `toList()` returns without any
 * subscriber/emitter coordination.
 */
private class FixedMotionDataSource(private val samples: List<MotionSample>) : MotionDataSource {
    override fun samples(): Flow<MotionSample> = samples.asFlow()
}
