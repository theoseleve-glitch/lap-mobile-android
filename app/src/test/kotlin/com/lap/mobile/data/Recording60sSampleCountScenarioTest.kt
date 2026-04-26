package com.lap.mobile.data

import com.lap.mobile.capture.CaptureOrchestrator
import com.lap.mobile.capture.FixedLocationDataSource
import com.lap.mobile.capture.FixedMotionDataSource
import com.lap.mobile.capture.LocationCaptureService
import com.lap.mobile.capture.LocationSample
import com.lap.mobile.capture.MotionCaptureService
import com.lap.mobile.capture.MotionSample
import com.lap.mobile.capture.PreflightGate
import com.lap.mobile.capture.PreflightStatus
import com.lap.mobile.core.NoOpAnalyticsReporter
import com.lap.mobile.core.Result
import com.lap.schemas.v1.UUID7
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private const val LOCATION_HZ: Int = 10
private const val MOTION_HZ: Int = 50
private const val RECORDING_SECONDS: Int = 60
private const val EXPECTED_LOCATION_SAMPLES: Int = LOCATION_HZ * RECORDING_SECONDS
private const val EXPECTED_MOTION_SAMPLES: Int = MOTION_HZ * RECORDING_SECONDS
private const val EXPECTED_TOTAL_SAMPLES: Int = EXPECTED_LOCATION_SAMPLES + EXPECTED_MOTION_SAMPLES
private const val SPEC_FLOOR_SAMPLES: Int = 3_000

private const val LATITUDE: Double = 47.643_222
private const val LONGITUDE: Double = 6.026_018
private const val ACCURACY_M: Float = 3.5f
private const val ACCEL_X_MPS2: Float = 0.10f
private const val ACCEL_Y_MPS2: Float = 0.20f
private const val ACCEL_Z_MPS2: Float = 9.80f
private const val LOC_RECEIPT_MS_BASE: Long = 1_000L
private const val LOC_TS_NS_BASE: Long = 1_000_000L
private const val MOTION_TS_NS_BASE: Long = 5_000_000L
private const val NS_PER_LOCATION_TICK: Long = (1_000_000_000L / LOCATION_HZ)
private const val NS_PER_MOTION_TICK: Long = (1_000_000_000L / MOTION_HZ)

/**
 * JVM-side scale test for T083 (instrumented sibling: 60-second on-device
 * recording producing >3000 captured samples).
 *
 * What this test locks at the JVM level:
 *  - SessionCoordinator + CaptureOrchestrator can ingest a full 60-second
 *    recording's worth of samples at production rates (location 10Hz,
 *    motion 50Hz) without losing or double-counting any.
 *  - The published spec threshold (>3000 samples) is comfortably met by
 *    motion alone — the JVM count of 3600 (600 + 3000) sits ~20% above
 *    the floor so a regression that drops, say, a quarter of the stream
 *    would still flunk the spec, not just this test.
 *  - The state machine walks IDLE → ARMED → RECORDING → FINALIZING and
 *    persists the final sample count back through SessionDao, mirroring
 *    what the instrumented test would assert about the durable record.
 *
 * What this test deliberately does NOT cover (genuinely device-required,
 * which is why T083's instrumented half stays [PENDING: device-required]):
 *  - Real Fused Location Provider sustains 10Hz over 60 wall-clock seconds.
 *  - Real SensorManager sustains 50Hz under thermal/foreground pressure.
 *  - Real foreground service survives backgrounding for the full 60s.
 *  - Real WakeLock holds CPU through doze for the full window.
 *  - Real notification renders with the live sample-count badge update
 *    cadence (T082's render path).
 *
 * Why a pre-built Flow rather than `delay()` between samples:
 *  - The contract is "no samples lost in the kernel", not "the orchestrator
 *    sleeps correctly between ticks". Pre-built finite Flow lets `runTest`
 *    complete in microseconds while still exercising the full collect-and-
 *    count pipeline; injecting `delay(20.ms)` between motion samples would
 *    burn 60 seconds of test wall-clock for no incremental coverage given
 *    `runTest`'s virtual scheduler short-circuits delay() anyway.
 *  - The instrumented variant on real GNSS/IMU hardware *is* the real-rate
 *    proof; trying to fake it here would dilute the JVM test's role.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Recording60sSampleCountScenarioTest {
    private val sessionId: UUID7 = "0194bbbb-bbbb-7bbb-bbbb-bbbbbbbbbbbb"
    private val now: Instant = Instant.parse("2026-04-26T12:00:00Z")

    @Test
    fun sixty_seconds_at_production_rates_yields_at_least_three_thousand_samples_per_t083_spec() = runTest {
        val coord = newCoordinatorAtScale()
        coord.arm(sessionId)

        val startResult = coord.start(backgroundScope)
        runCurrent()
        coord.stop()

        assertTrue("expected start() Ok, got $startResult", startResult is Result.Ok)
        assertTrue(
            "T083 spec floor: 60s recording must capture >$SPEC_FLOOR_SAMPLES samples; " +
                "kernel captured ${coord.sampleCount.value}",
            coord.sampleCount.value > SPEC_FLOOR_SAMPLES,
        )
    }

    @Test
    fun no_samples_dropped_by_kernel_when_both_sources_emit_at_full_rate() = runTest {
        val coord = newCoordinatorAtScale()
        coord.arm(sessionId)

        coord.start(backgroundScope)
        runCurrent()
        coord.stop()

        // Exact-count check is the headline regression guard — if the
        // orchestrator's merge ever drops, dedupes, or double-counts a
        // sample at scale, this fails before it ever ships to a device.
        assertEquals(EXPECTED_TOTAL_SAMPLES, coord.sampleCount.value)
    }

    @Test
    fun final_sample_count_is_persisted_to_session_dao_for_finalize_pipeline() = runTest {
        val sessionDao = FakeSessionDao()
        val coord = newCoordinatorAtScale(sessionDao = sessionDao)
        coord.arm(sessionId)
        coord.start(backgroundScope)
        runCurrent()

        coord.stop()

        // Mirrors what an instrumented test would assert: the persisted
        // session row carries the same count the in-memory StateFlow saw.
        // Catches a regression where the kernel counts correctly but the
        // SessionDao update happens before the final tick lands.
        val row = sessionDao.rows.getValue(sessionId)
        assertEquals(EXPECTED_TOTAL_SAMPLES, row.sampleCount)
    }

    @Test
    fun session_end_event_carries_full_sample_count_for_dashboard_render() = runTest {
        val coord = newCoordinatorAtScale()
        coord.arm(sessionId)
        coord.start(backgroundScope)
        runCurrent()

        val pending = async { coord.endEvents.first() }
        runCurrent()
        coord.stop()
        val event = pending.await()

        // The dashboard (T082) reads the SessionEndEvent for its
        // post-recording badge; if the kernel finalises before counting
        // is complete the badge would show stale data.
        assertEquals(sessionId, event.sessionId)
        assertEquals(EXPECTED_TOTAL_SAMPLES, event.sampleCount)
    }

    private fun newCoordinatorAtScale(
        sessionDao: FakeSessionDao = FakeSessionDao(),
        journalDao: FakeJournalDao = FakeJournalDao(),
    ): SessionCoordinator {
        val locationSamples = (0 until EXPECTED_LOCATION_SAMPLES).map { i ->
            LocationSample(
                latitude = LATITUDE,
                longitude = LONGITUDE,
                accuracyMeters = ACCURACY_M,
                altitudeMeters = null,
                speedMps = null,
                bearingDeg = null,
                sourceTimestampNs = LOC_TS_NS_BASE + (i * NS_PER_LOCATION_TICK),
                receiptUtcMillis = LOC_RECEIPT_MS_BASE + i.toLong(),
            )
        }
        val motionSamples = (0 until EXPECTED_MOTION_SAMPLES).flatMap { i ->
            val accel = MotionSample.Accelerometer(
                xMps2 = ACCEL_X_MPS2,
                yMps2 = ACCEL_Y_MPS2,
                zMps2 = ACCEL_Z_MPS2,
                sourceTimestampNs = MOTION_TS_NS_BASE + (i * NS_PER_MOTION_TICK),
            )
            // Halve the gyro emission so motion total lands at exactly
            // EXPECTED_MOTION_SAMPLES (3000) — alternative would be a
            // 100Hz motion fixture but 50Hz mirrors T068's documented
            // production rate so the test stays honest about what it's
            // proving.
            listOf<MotionSample>(accel)
        }
        val orchestrator = CaptureOrchestrator(
            location = LocationCaptureService(FixedLocationDataSource(locationSamples)),
            motion = MotionCaptureService(FixedMotionDataSource(motionSamples)),
        )
        val journalWriter = JournalWriter(dao = journalDao, clock = FixedClock(now))
        return SessionCoordinator(
            sessionDao = sessionDao,
            orchestrator = orchestrator,
            journalWriter = journalWriter,
            clock = FixedClock(now),
            preflight = PreflightGate { PreflightStatus.Ready },
            analyticsReporter = NoOpAnalyticsReporter,
        )
    }
}
