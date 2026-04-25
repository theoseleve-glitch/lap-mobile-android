package com.lap.mobile.capture

import com.lap.schemas.v1.TelemetrySample
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
private const val LOC_TS_NS_C: Long = 3_000L
private const val LOC_RECEIPT_MS: Long = 10L

private const val ACCEL_X_MPS2: Float = 0.10f
private const val ACCEL_Y_MPS2: Float = 0.20f
private const val ACCEL_Z_MPS2: Float = 9.80f
private const val GYRO_X_RPS: Float = 0.001f
private const val GYRO_Y_RPS: Float = 0.002f
private const val GYRO_Z_RPS: Float = 0.003f
private const val MOTION_TS_NS_A: Long = 11_000L
private const val MOTION_TS_NS_B: Long = 12_000L
private const val MOTION_TS_NS_C: Long = 13_000L
private const val MOTION_TS_NS_D: Long = 14_000L

private const val LOCATION_SAMPLE_COUNT: Int = 3
private const val MOTION_SAMPLE_COUNT: Int = 4
private const val EXPECTED_MERGED_COUNT: Int = LOCATION_SAMPLE_COUNT + MOTION_SAMPLE_COUNT

private val GNSS_CHANNEL_SET: Set<String> = setOf(
    LocationChannels.LATITUDE,
    LocationChannels.LONGITUDE,
    LocationChannels.ACCURACY_M,
    LocationChannels.SPEED_MPS,
    LocationChannels.BEARING_DEG,
    LocationChannels.ALTITUDE_M,
)

private val MOTION_CHANNEL_SET: Set<String> = setOf(
    MotionChannels.ACCEL_X_MPS2,
    MotionChannels.ACCEL_Y_MPS2,
    MotionChannels.ACCEL_Z_MPS2,
    MotionChannels.GYRO_X_RPS,
    MotionChannels.GYRO_Y_RPS,
    MotionChannels.GYRO_Z_RPS,
)

/**
 * The no-on-device-fusion contract (T072), promoted from an
 * orchestrator-internal property to a separate regression guard. Per
 * ADR-007 §392, mobile clients ship raw sensor readings verbatim and
 * never combine across sources — server canonicalization happens in
 * the ADR-012 ingestion pipeline.
 *
 * Three observable invariants prove the rule:
 *
 *  1. Every emitted [TelemetrySample] has `tCanonicalNs == tSourceNs`.
 *     Any divergence means somebody pre-canonicalized on-device.
 *  2. Every emitted sample's channel keys are a subset of exactly one
 *     source category (gnss XOR motion). Crossing categories would
 *     mean a fused sample.
 *  3. Sample count preservation: `N location + M motion → N + M
 *     outputs`. Any drop or merge changes the count.
 *
 * Tests stay JVM-pure — no Android, no instrumentation needed to lock
 * the contract.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NoFusionContractTest {
    @Test
    fun location_mapper_keeps_canonical_equal_to_source() {
        val sample = locationSample(LOC_TS_NS_A)
        val ts = sample.toTelemetrySample()
        assertEquals(ts.tSourceNs, ts.tCanonicalNs)
    }

    @Test
    fun motion_mapper_keeps_canonical_equal_to_source_for_both_subtypes() {
        val accelTs = accelSample(MOTION_TS_NS_A).toTelemetrySample()
        val gyroTs = gyroSample(MOTION_TS_NS_B).toTelemetrySample()
        assertEquals(accelTs.tSourceNs, accelTs.tCanonicalNs)
        assertEquals(gyroTs.tSourceNs, gyroTs.tCanonicalNs)
    }

    @Test
    fun orchestrator_preserves_canonical_equal_to_source_on_every_emission() = runTest {
        val merged = mixedOrchestrator().samples().toList()

        merged.forEachIndexed { index, ts ->
            assertEquals(
                "sample[$index] (clock=${ts.tSourceClock}) must have tCanonicalNs == tSourceNs",
                ts.tSourceNs,
                ts.tCanonicalNs,
            )
        }
    }

    @Test
    fun orchestrator_never_emits_sample_whose_channels_cross_source_categories() = runTest {
        val merged = mixedOrchestrator().samples().toList()

        merged.forEachIndexed { index, ts ->
            val keys = ts.channels.keys
            val hasGnss = keys.any { it in GNSS_CHANNEL_SET }
            val hasMotion = keys.any { it in MOTION_CHANNEL_SET }
            assertTrue(
                "sample[$index] mixes GNSS and motion channel keys → fusion bug. keys=$keys",
                !(hasGnss && hasMotion),
            )
        }
    }

    @Test
    fun orchestrator_preserves_sample_count_across_sources() = runTest {
        val merged = mixedOrchestrator().samples().toList()

        // N location + M motion → N + M outputs. Any merge or drop changes the count.
        assertEquals(EXPECTED_MERGED_COUNT, merged.size)
    }

    @Test
    fun orchestrator_distinct_source_clocks_remain_distinct_across_emissions() = runTest {
        val merged = mixedOrchestrator().samples().toList()

        // No emission should use a source clock the upstream didn't carry.
        val distinctClocks = merged.map { it.tSourceClock }.toSet()
        assertEquals(setOf("gnss", "device_monotonic"), distinctClocks)
    }

    @Test
    fun orchestrator_never_blends_a_clock_label_into_an_unrelated_channel_set() = runTest {
        val merged = mixedOrchestrator().samples().toList()

        merged.forEachIndexed { index, ts ->
            when (ts.tSourceClock) {
                "gnss" -> assertTrue(
                    "sample[$index] tagged gnss must carry only GNSS channels. keys=${ts.channels.keys}",
                    ts.channels.keys.all { it in GNSS_CHANNEL_SET },
                )
                "device_monotonic" -> assertTrue(
                    "sample[$index] tagged device_monotonic must carry only motion channels. keys=${ts.channels.keys}",
                    ts.channels.keys.all { it in MOTION_CHANNEL_SET },
                )
                else -> error("unexpected tSourceClock=${ts.tSourceClock} on sample[$index]")
            }
        }
    }

    private fun mixedOrchestrator(): CaptureOrchestrator = CaptureOrchestrator(
        location = LocationCaptureService(
            FixedLocationDataSource(
                listOf(
                    locationSample(LOC_TS_NS_A),
                    locationSample(LOC_TS_NS_B),
                    locationSample(LOC_TS_NS_C),
                ),
            ),
        ),
        motion = MotionCaptureService(
            FixedMotionDataSource(
                listOf(
                    accelSample(MOTION_TS_NS_A),
                    accelSample(MOTION_TS_NS_B),
                    gyroSample(MOTION_TS_NS_C),
                    gyroSample(MOTION_TS_NS_D),
                ),
            ),
        ),
    )

    private fun locationSample(sourceNs: Long): LocationSample = LocationSample(
        latitude = LATITUDE,
        longitude = LONGITUDE,
        accuracyMeters = ACCURACY_M,
        altitudeMeters = null,
        speedMps = null,
        bearingDeg = null,
        sourceTimestampNs = sourceNs,
        receiptUtcMillis = LOC_RECEIPT_MS,
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
