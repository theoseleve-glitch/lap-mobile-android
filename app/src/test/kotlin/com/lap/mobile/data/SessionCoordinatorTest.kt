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
import com.lap.mobile.core.DomainError
import com.lap.mobile.core.Result
import com.lap.schemas.v1.UUID7
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private const val LATITUDE: Double = 47.643_222
private const val LONGITUDE: Double = 6.026_018
private const val ACCURACY_M: Float = 3.5f
private const val LOC_TS_NS_BASE: Long = 1_000L
private const val LOC_RECEIPT_MS: Long = 10L

private const val SAMPLE_COUNT_FIXTURE: Int = 4
private const val HEARTBEAT_VIRTUAL_ADVANCE_MS: Long = 15_000L
private const val EXPECTED_HEARTBEATS_OVER_15S: Int = 4
private const val WAIT_FOR_NO_EVENT_MS: Long = 200L

/**
 * Integration test for [SessionCoordinator] (T076). Exercises the
 * IDLE→ARMED→RECORDING→FINALIZING walk end-to-end with the real
 * [CaptureOrchestrator] + [JournalWriter] + [SessionDao] (in-memory
 * fake) wired together.
 *
 * Virtual-time `runTest` lets the journal heartbeat (5 s cadence)
 * fire several times in microseconds of wall time. The capture
 * orchestrator is fed deterministic finite flows so sample counting
 * has a known expected value; the journal coroutine runs forever
 * until [SessionCoordinator.stop] cancels it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionCoordinatorTest {
    private val sessionId: UUID7 = "0194aaaa-aaaa-7aaa-aaaa-aaaaaaaaaaaa"
    private val now: Instant = Instant.parse("2026-04-25T09:00:00Z")

    @Test
    fun initial_state_is_idle() = runTest {
        val coord = newCoordinator(emptyList(), emptyList())

        assertEquals(RecordingState.IDLE, coord.state.value)
    }

    @Test
    fun arm_transitions_to_armed_and_writes_session_row() = runTest {
        val sessionDao = FakeSessionDao()
        val coord = newCoordinator(emptyList(), emptyList(), sessionDao = sessionDao)

        val result = coord.arm(sessionId)

        assertTrue("expected Ok, got $result", result is Result.Ok)
        assertEquals(RecordingState.ARMED, coord.state.value)
        assertEquals(RecordingState.ARMED.wireValue, sessionDao.rows.getValue(sessionId).state)
    }

    @Test
    fun start_without_arm_returns_validation_error_and_keeps_state() = runTest {
        val coord = newCoordinator(emptyList(), emptyList())

        val result = coord.start(backgroundScope)

        assertTrue("expected Err, got $result", result is Result.Err)
        assertEquals(RecordingState.IDLE, coord.state.value)
    }

    @Test
    fun start_after_arm_transitions_to_recording_and_collects_samples() = runTest {
        val sessionDao = FakeSessionDao()
        val locationSamples = (1..SAMPLE_COUNT_FIXTURE).map { i ->
            locationSample(LOC_TS_NS_BASE + i)
        }
        val coord = newCoordinator(locationSamples, emptyList(), sessionDao = sessionDao)
        coord.arm(sessionId)

        val result = coord.start(backgroundScope)
        runCurrent()

        assertTrue("expected Ok, got $result", result is Result.Ok)
        assertEquals(RecordingState.RECORDING, coord.state.value)
        assertEquals(SAMPLE_COUNT_FIXTURE, coord.sampleCount.value)
        val row = sessionDao.rows.getValue(sessionId)
        assertEquals(RecordingState.RECORDING.wireValue, row.state)
        assertNotNull("startedAt should be set on RECORDING", row.startedAt)
    }

    @Test
    fun start_kicks_off_journal_heartbeats() = runTest {
        val journalDao = FakeJournalDao()
        val coord = newCoordinator(emptyList(), emptyList(), journalDao = journalDao)
        coord.arm(sessionId)
        coord.start(backgroundScope)

        runCurrent()
        // After t=0 there's been one heartbeat. Advance 15s to get 3 more (t=5, 10, 15).
        advanceTimeBy(HEARTBEAT_VIRTUAL_ADVANCE_MS)
        runCurrent()

        assertEquals(EXPECTED_HEARTBEATS_OVER_15S, journalDao.upsertCalls)
        assertEquals(sessionId, journalDao.rows.getValue(sessionId).sessionId)

        // Cancel coordinator to release the journal coroutine before runTest tears down.
        coord.stop()
    }

    @Test
    fun stop_transitions_to_finalizing_and_cancels_capture_jobs() = runTest {
        val journalDao = FakeJournalDao()
        val coord = newCoordinator(emptyList(), emptyList(), journalDao = journalDao)
        coord.arm(sessionId)
        coord.start(backgroundScope)
        advanceTimeBy(HEARTBEAT_VIRTUAL_ADVANCE_MS)
        runCurrent()
        val heartbeatsBeforeStop = journalDao.upsertCalls

        coord.stop()
        // Journal must NOT continue heartbeating after stop.
        advanceTimeBy(HEARTBEAT_VIRTUAL_ADVANCE_MS)
        runCurrent()

        assertEquals(RecordingState.FINALIZING, coord.state.value)
        assertEquals(
            "no further heartbeats after stop()",
            heartbeatsBeforeStop,
            journalDao.upsertCalls,
        )
    }

    @Test
    fun stop_persists_sample_count_and_ended_at() = runTest {
        val sessionDao = FakeSessionDao()
        val locationSamples = (1..SAMPLE_COUNT_FIXTURE).map { i -> locationSample(LOC_TS_NS_BASE + i) }
        val coord = newCoordinator(locationSamples, emptyList(), sessionDao = sessionDao)
        coord.arm(sessionId)
        coord.start(backgroundScope)
        runCurrent()

        coord.stop()

        val row = sessionDao.rows.getValue(sessionId)
        assertEquals(SAMPLE_COUNT_FIXTURE, row.sampleCount)
        assertNotNull("endedAt must be set on FINALIZING", row.endedAt)
        assertEquals(RecordingState.FINALIZING.wireValue, row.state)
    }

    @Test
    fun stop_from_idle_returns_error_without_state_change() = runTest {
        val coord = newCoordinator(emptyList(), emptyList())

        val result = coord.stop()

        assertTrue("expected Err, got $result", result is Result.Err)
        assertEquals(RecordingState.IDLE, coord.state.value)
    }

    @Test
    fun arm_from_recording_returns_error_without_state_change() = runTest {
        val coord = newCoordinator(emptyList(), emptyList())
        coord.arm(sessionId)
        coord.start(backgroundScope)
        runCurrent()

        val result = coord.arm(sessionId)

        assertTrue("expected Err, got $result", result is Result.Err)
        assertEquals(RecordingState.RECORDING, coord.state.value)

        coord.stop()
    }

    @Test
    fun start_with_failing_preflight_returns_error_and_stays_armed() = runTest {
        val notReady = PreflightStatus.NotReady(reasons = listOf("location_permission", "gps_accuracy"))
        val coord = newCoordinator(emptyList(), emptyList(), preflight = PreflightGate { notReady })
        coord.arm(sessionId)

        val result = coord.start(backgroundScope)

        assertTrue("expected Err, got $result", result is Result.Err)
        val err = (result as Result.Err).error
        assertTrue("expected Validation, got $err", err is DomainError.Validation)
        // Reasons must surface so the UI (T097) can render specifics.
        val message = (err as DomainError.Validation).message
        assertTrue("message must mention location_permission, was: $message", "location_permission" in message)
        assertTrue("message must mention gps_accuracy, was: $message", "gps_accuracy" in message)
        assertEquals(RecordingState.ARMED, coord.state.value)
    }

    @Test
    fun start_with_passing_preflight_proceeds_to_recording() = runTest {
        val coord = newCoordinator(emptyList(), emptyList(), preflight = PreflightGate { PreflightStatus.Ready })
        coord.arm(sessionId)

        val result = coord.start(backgroundScope)
        runCurrent()

        assertTrue("expected Ok, got $result", result is Result.Ok)
        assertEquals(RecordingState.RECORDING, coord.state.value)

        coord.stop()
    }

    @Test
    fun stop_emits_session_end_event_with_session_id_ended_at_and_sample_count() = runTest {
        val locationSamples = (1..SAMPLE_COUNT_FIXTURE).map { i -> locationSample(LOC_TS_NS_BASE + i) }
        val coord = newCoordinator(locationSamples, emptyList())
        coord.arm(sessionId)
        coord.start(backgroundScope)
        runCurrent()

        val pending = async { coord.endEvents.first() }
        runCurrent()
        coord.stop()
        val event = pending.await()

        assertEquals(sessionId, event.sessionId)
        assertEquals(SAMPLE_COUNT_FIXTURE, event.sampleCount)
        // endedAt comes from the same FixedClock the coordinator uses.
        assertEquals(now, event.endedAt)
    }

    @Test
    fun stop_returning_error_does_not_emit_event() = runTest {
        val coord = newCoordinator(emptyList(), emptyList())
        // Try to stop from IDLE — invalid transition, no event should fire.

        val maybeEvent = withTimeoutOrNull(WAIT_FOR_NO_EVENT_MS) {
            val pending = async { coord.endEvents.first() }
            runCurrent()
            coord.stop()
            pending.await()
        }

        // No event arrived during the wait window — withTimeoutOrNull returns null.
        assertNull("stop() returning Err must NOT emit a SessionEndEvent", maybeEvent)
    }

    @Test
    fun preflight_is_not_called_when_state_already_blocks_transition() = runTest {
        var preflightInvocations = 0
        val gate = PreflightGate {
            preflightInvocations++
            PreflightStatus.Ready
        }
        val coord = newCoordinator(emptyList(), emptyList(), preflight = gate)
        // No arm() — start() is invalid from IDLE before preflight even matters.

        coord.start(backgroundScope)

        assertEquals("state-machine guard must short-circuit before preflight runs", 0, preflightInvocations)
    }

    private fun newCoordinator(
        locationSamples: List<LocationSample>,
        motionSamples: List<MotionSample>,
        sessionDao: FakeSessionDao = FakeSessionDao(),
        journalDao: FakeJournalDao = FakeJournalDao(),
        preflight: PreflightGate = PreflightGate { PreflightStatus.Ready },
    ): SessionCoordinator {
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
            preflight = preflight,
        )
    }

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
}
