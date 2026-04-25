package com.lap.mobile.capture

import com.lap.schemas.v1.TelemetrySample
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val LATITUDE: Double = 47.643_222
private const val LONGITUDE: Double = 6.026_018
private const val ACCURACY_M: Float = 3.5f
private const val LOC_TS_NS_A: Long = 1_000L
private const val LOC_TS_NS_B: Long = 2_000L
private const val LOC_RECEIPT_MS_A: Long = 10L
private const val LOC_RECEIPT_MS_B: Long = 20L

private const val ACCEL_X_MPS2: Float = 0.10f
private const val ACCEL_Y_MPS2: Float = 0.20f
private const val ACCEL_Z_MPS2: Float = 9.80f
private const val GYRO_X_RPS: Float = 0.001f
private const val GYRO_Y_RPS: Float = 0.002f
private const val GYRO_Z_RPS: Float = 0.003f
private const val MOTION_TS_NS_A: Long = 11_000L
private const val MOTION_TS_NS_B: Long = 12_000L

private const val EXTRA_TS_NS: Long = 99_999L
private const val EXTRA_CHANNEL_VALUE: Double = 42.0

/**
 * The orchestrator (T071) is a pure flow-combinator over the location
 * and motion services plus any additional pre-mapped source (BLE
 * lands at T108). The job is "merge into one stream and apply the
 * per-source mapper"; the test cases lock:
 *
 *  - empty inputs → empty merged stream
 *  - location samples come through tagged `tSourceClock="gnss"`
 *  - motion samples come through tagged `tSourceClock="device_monotonic"`
 *  - merging both preserves count (no dropped or duplicated samples)
 *  - additional pre-mapped sources are merged in alongside
 *  - merge does not pre-fuse — each sample retains its source clock
 *    (per-sample tagging contract that T072 will extend)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CaptureOrchestratorTest {
    @Test
    fun empty_location_and_motion_yields_empty_merged_flow() = runTest {
        val orchestrator = orchestrator(emptyList(), emptyList())

        val merged = orchestrator.samples().toList()

        assertEquals(emptyList<TelemetrySample>(), merged)
    }

    @Test
    fun location_only_emits_gnss_tagged_samples() = runTest {
        val locA = locationSample(LOC_TS_NS_A, LOC_RECEIPT_MS_A)
        val locB = locationSample(LOC_TS_NS_B, LOC_RECEIPT_MS_B)
        val orchestrator = orchestrator(locationSamples = listOf(locA, locB), motionSamples = emptyList())

        val merged = orchestrator.samples().toList()

        assertEquals(2, merged.size)
        assertTrue("all location samples must carry gnss clock", merged.all { it.tSourceClock == "gnss" })
    }

    @Test
    fun motion_only_emits_device_monotonic_tagged_samples() = runTest {
        val accel = accelSample(MOTION_TS_NS_A)
        val gyro = gyroSample(MOTION_TS_NS_B)
        val orchestrator = orchestrator(emptyList(), listOf(accel, gyro))

        val merged = orchestrator.samples().toList()

        assertEquals(2, merged.size)
        assertTrue(
            "all motion samples must carry device_monotonic clock",
            merged.all { it.tSourceClock == "device_monotonic" },
        )
    }

    @Test
    fun merging_location_and_motion_preserves_total_sample_count() = runTest {
        val locA = locationSample(LOC_TS_NS_A, LOC_RECEIPT_MS_A)
        val locB = locationSample(LOC_TS_NS_B, LOC_RECEIPT_MS_B)
        val accel = accelSample(MOTION_TS_NS_A)
        val gyro = gyroSample(MOTION_TS_NS_B)
        val orchestrator = orchestrator(listOf(locA, locB), listOf(accel, gyro))

        val merged = orchestrator.samples().toList()

        assertEquals(4, merged.size)
    }

    @Test
    fun merge_preserves_per_sample_source_clock_tag() = runTest {
        val loc = locationSample(LOC_TS_NS_A, LOC_RECEIPT_MS_A)
        val accel = accelSample(MOTION_TS_NS_A)
        val orchestrator = orchestrator(listOf(loc), listOf(accel))

        val merged = orchestrator.samples().toList()

        // Each sample retains its origin clock — no on-device fusion (ADR-007).
        val clocks = merged.map { it.tSourceClock }.toSet()
        assertEquals(setOf("gnss", "device_monotonic"), clocks)
    }

    @Test
    fun additional_source_is_merged_alongside_location_and_motion() = runTest {
        val loc = locationSample(LOC_TS_NS_A, LOC_RECEIPT_MS_A)
        val accel = accelSample(MOTION_TS_NS_A)
        val extra = TelemetrySample(
            tSourceNs = EXTRA_TS_NS,
            tCanonicalNs = EXTRA_TS_NS,
            tSourceClock = "racebox_gnss",
            channels = mapOf("synthetic" to EXTRA_CHANNEL_VALUE),
        )
        val extraSource: Flow<TelemetrySample> = flowOf(extra)
        val orchestrator = CaptureOrchestrator(
            location = LocationCaptureService(FixedLocationDataSource(listOf(loc))),
            motion = MotionCaptureService(FixedMotionDataSource(listOf(accel))),
            additionalSources = listOf(extraSource),
        )

        val merged = orchestrator.samples().toList()

        assertEquals(3, merged.size)
        assertTrue(merged.any { it.tSourceClock == "racebox_gnss" })
    }

    private fun orchestrator(
        locationSamples: List<LocationSample>,
        motionSamples: List<MotionSample>,
    ): CaptureOrchestrator = CaptureOrchestrator(
        location = LocationCaptureService(FixedLocationDataSource(locationSamples)),
        motion = MotionCaptureService(FixedMotionDataSource(motionSamples)),
    )

    private fun locationSample(sourceNs: Long, receiptMs: Long): LocationSample = LocationSample(
        latitude = LATITUDE,
        longitude = LONGITUDE,
        accuracyMeters = ACCURACY_M,
        altitudeMeters = null,
        speedMps = null,
        bearingDeg = null,
        sourceTimestampNs = sourceNs,
        receiptUtcMillis = receiptMs,
    )

    private fun accelSample(sourceNs: Long): MotionSample = MotionSample.Accelerometer(
        xMps2 = ACCEL_X_MPS2,
        yMps2 = ACCEL_Y_MPS2,
        zMps2 = ACCEL_Z_MPS2,
        sourceTimestampNs = sourceNs,
    )

    private fun gyroSample(sourceNs: Long): MotionSample = MotionSample.Gyroscope(
        xRadPerSec = GYRO_X_RPS,
        yRadPerSec = GYRO_Y_RPS,
        zRadPerSec = GYRO_Z_RPS,
        sourceTimestampNs = sourceNs,
    )
}
