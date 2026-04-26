package com.lap.mobile.data

import com.lap.mobile.capture.CaptureOrchestrator
import com.lap.mobile.capture.FixedLocationDataSource
import com.lap.mobile.capture.FixedMotionDataSource
import com.lap.mobile.capture.LocationCaptureService
import com.lap.mobile.capture.LocationSample
import com.lap.mobile.capture.MotionCaptureService
import com.lap.mobile.capture.PreflightGate
import com.lap.mobile.capture.PreflightStatus
import com.lap.mobile.core.DomainError
import com.lap.mobile.core.NoOpAnalyticsReporter
import com.lap.mobile.core.Result
import com.lap.schemas.v1.UUID7
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private const val LATITUDE: Double = 47.643_222
private const val LONGITUDE: Double = 6.026_018
private const val ACCURACY_M: Float = 3.5f
private const val LOC_TS_NS_BASE: Long = 1_000L
private const val LOC_RECEIPT_MS_BASE: Long = 10L
private const val PRE_KILL_LOCATION_SAMPLES: Int = 50
private const val PRE_KILL_VIRTUAL_ADVANCE_MS: Long = 15_000L
private const val POST_KILL_VIRTUAL_ADVANCE_MS: Long = 15_000L
private const val RECOVERED_SAMPLE_COUNT: Int = 27_500
private const val RECOVERED_FILE_URI: String = "file:///bundles/recovered.bundle"

/**
 * JVM-side end-to-end scenario for T084 (instrumented sibling: kill app
 * during recording → restart → journal recovery).
 *
 * The two halves of the recovery pipeline already have isolation tests:
 *  - [CrashedSessionReconcilerTest] proves "find sessions stuck in
 *    `recording`/`finalizing` at startup".
 *  - [FinalizeFromJournalTest] proves "walk a crashed-session row +
 *    journal row to a finalized bundle".
 *
 * What was missing — and what T084 specifically asks for — is the
 * cohesive end-to-end: an active recording is killed *without graceful
 * stop*, the in-memory state is gone, and the durable
 * SessionDao+JournalDao state is exactly what the recovery pipeline
 * needs to walk back to a completed bundle. A regression where any of
 * these three contracts breaks would silently destroy crashed-session
 * recovery without the isolation tests catching it:
 *  - SessionCoordinator.start() must persist `state="recording"` to
 *    SessionDao before the captureJob/journalJob are launched (so the
 *    row survives a kill that fires the very next instant).
 *  - JournalWriter must heartbeat at least once at t=0 (so a kill
 *    inside the first 5 s leaves a usable journal row).
 *  - Cancelling the capture scope (the JVM analogue of OS-level process
 *    death) must NOT trigger any cleanup that flips state away from
 *    `recording` — the orderly shutdown only happens via `coord.stop()`,
 *    which the kill scenario by definition skips.
 *
 * What this test deliberately does NOT cover (genuinely device-required,
 * which is why T084's instrumented half stays
 * [PENDING: device-required]):
 *  - Real `Process.killProcess()` from a parent test process against
 *    a child app process while the foreground service is recording
 *    (instrumented harness territory; the BLOCKED note flagged this
 *    as needing a real recording-screen wiring + Hilt graph).
 *  - Real ANR / OS-killed-low-memory scenarios (different death modes
 *    than orderly cancellation; the WakeLock + foreground service are
 *    designed to survive these but only on-device proves it).
 *  - Real cold-start of the recovery flow against a real Room DB on
 *    disk (vs. in-memory FakeSessionDao + FakeJournalDao here).
 *
 * Why a custom killable scope rather than `backgroundScope`:
 *  - `backgroundScope` is owned by `runTest` and cancelled at teardown,
 *    so cancelling it mid-test would cancel everything else. We need a
 *    scope whose cancellation models *only* the recording-process death
 *    while the test's recovery-side reads against the persisted DAO
 *    state continue to run.
 *  - `CoroutineScope(coroutineContext + Job())` shares the test
 *    scheduler (so virtual time + `runCurrent`/`advanceTimeBy` work)
 *    while giving us an independently cancellable Job tree.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KillAndRestartJournalRecoveryScenarioTest {
    private val sessionId: UUID7 = "0194cccc-cccc-7ccc-cccc-cccccccccccc"
    private val now: Instant = Instant.parse("2026-04-26T13:00:00Z")
    private val recoveredEndedAt: Instant = Instant.parse("2026-04-26T13:45:00Z")

    @Test
    fun mid_session_kill_then_restart_finalize_walks_recording_back_to_completed() = runTest {
        val sessionDao = FakeSessionDao()
        val journalDao = FakeJournalDao()
        val locationSamples = (1..PRE_KILL_LOCATION_SAMPLES).map { i ->
            locationSample(LOC_TS_NS_BASE + i)
        }

        // Phase 1 — active recording before the kill.
        val killableScope = CoroutineScope(coroutineContext + Job())
        val coord = newCoordinator(locationSamples, sessionDao, journalDao)
        coord.arm(sessionId)
        coord.start(killableScope)
        runCurrent()
        advanceTimeBy(PRE_KILL_VIRTUAL_ADVANCE_MS)
        runCurrent()

        val preKillSessionRow = sessionDao.rows.getValue(sessionId)
        assertEquals(
            "pre-kill state must be 'recording' so reconciler will pick it up",
            RecordingState.RECORDING.wireValue,
            preKillSessionRow.state,
        )
        val preKillJournalRow = journalDao.rows.getValue(sessionId)
        assertNotNull("journal row must exist before kill", preKillJournalRow)
        val preKillFileOffset = preKillJournalRow.fileOffset

        // Phase 2 — kill (process death simulated by scope cancel; no
        // graceful stop). Advance more virtual time after the kill to
        // confirm zero further DAO writes happen — the kill must be
        // truly terminal for the recording coroutines.
        killableScope.cancel()
        runCurrent()
        advanceTimeBy(POST_KILL_VIRTUAL_ADVANCE_MS)
        runCurrent()

        val postKillSessionRow = sessionDao.rows.getValue(sessionId)
        assertEquals(
            "post-kill state must still be 'recording' — only stop() flips to finalizing",
            RecordingState.RECORDING.wireValue,
            postKillSessionRow.state,
        )
        val postKillJournalRow = journalDao.rows.getValue(sessionId)
        assertEquals(
            "no further heartbeats should land after the kill",
            preKillFileOffset,
            postKillJournalRow.fileOffset,
        )

        // Phase 3 — restart: the reconciler discovers the crashed session.
        val reconciler = CrashedSessionReconciler(sessionDao)
        val recoverable = reconciler.findRecoverable()
        assertEquals(
            "kill must leave exactly one recoverable session for the recovery modal",
            listOf(sessionId),
            recoverable.map { it.id },
        )

        // Phase 4 — restart: user picks Recovery → FinalizeFromJournal
        // walks the row + journal back to a completed bundle.
        val finalizer = RecoveryFakeBundleFinalizer(
            BundleFinalizer.FinalizedBundle(
                endedAt = recoveredEndedAt,
                fileUri = RECOVERED_FILE_URI,
                sampleCount = RECOVERED_SAMPLE_COUNT,
            ),
        )
        val useCase = FinalizeFromJournal(sessionDao, journalDao, finalizer)

        val result = useCase.run(sessionId)

        assertTrue("recovery must succeed, got $result", result is Result.Ok)
        val recoveredRow = sessionDao.rows.getValue(sessionId)
        assertEquals(
            "recovery must transition recording → completed (per RecordingState diagram)",
            RecordingState.COMPLETED.wireValue,
            recoveredRow.state,
        )
        assertEquals(recoveredEndedAt, recoveredRow.endedAt)
        assertEquals(RECOVERED_FILE_URI, recoveredRow.fileUri)
        assertEquals(RECOVERED_SAMPLE_COUNT, recoveredRow.sampleCount)
        assertNotNull(
            "finalizer must have been called with the kill-time session row",
            finalizer.capturedSession,
        )
        assertEquals(sessionId, finalizer.capturedSession?.id)
        assertEquals(postKillJournalRow, finalizer.capturedJournal)
    }

    @Test
    fun journal_row_exists_at_t_zero_so_kill_inside_first_heartbeat_window_is_still_recoverable() = runTest {
        val sessionDao = FakeSessionDao()
        val journalDao = FakeJournalDao()
        val killableScope = CoroutineScope(coroutineContext + Job())
        val coord = newCoordinator(emptyList(), sessionDao, journalDao)
        coord.arm(sessionId)
        coord.start(killableScope)
        runCurrent()

        // Kill inside the first 5 s window — only the t=0 heartbeat
        // has fired. Without it, the journal row would not exist and
        // the reconciler would surface a "recording" session that
        // FinalizeFromJournal can't actually walk back. Locks
        // JournalWriter's "first tick fires immediately" contract.
        killableScope.cancel()
        runCurrent()

        val journalRowAfterEarlyKill = journalDao.rows[sessionId]
        assertNotNull(
            "journal row must exist even after a kill inside the first heartbeat window",
            journalRowAfterEarlyKill,
        )
        val recoverable = CrashedSessionReconciler(sessionDao).findRecoverable()
        assertEquals(listOf(sessionId), recoverable.map { it.id })

        val finalizer = RecoveryFakeBundleFinalizer(
            BundleFinalizer.FinalizedBundle(
                endedAt = recoveredEndedAt,
                fileUri = RECOVERED_FILE_URI,
                sampleCount = 0,
            ),
        )
        val result = FinalizeFromJournal(sessionDao, journalDao, finalizer).run(sessionId)
        assertTrue("recovery must succeed even with t=0-kill journal, got $result", result is Result.Ok)
    }

    @Test
    fun finalizer_error_during_recovery_marks_session_failed_so_user_can_discard() = runTest {
        val sessionDao = FakeSessionDao()
        val journalDao = FakeJournalDao()
        val killableScope = CoroutineScope(coroutineContext + Job())
        val coord = newCoordinator(emptyList(), sessionDao, journalDao)
        coord.arm(sessionId)
        coord.start(killableScope)
        runCurrent()
        advanceTimeBy(PRE_KILL_VIRTUAL_ADVANCE_MS)
        runCurrent()
        killableScope.cancel()
        runCurrent()

        val finalizer = RecoveryFakeBundleFinalizer(
            error = DomainError.Io(message = "buffer file truncated past last journal offset"),
        )
        val result = FinalizeFromJournal(sessionDao, journalDao, finalizer).run(sessionId)

        assertTrue("expected Err for finalizer failure, got $result", result is Result.Err)
        val failedRow = sessionDao.rows.getValue(sessionId)
        assertEquals(
            "finalizer error must flip recovery row to 'failed' so the user is offered Discard",
            RecordingState.FAILED.wireValue,
            failedRow.state,
        )
    }

    private fun newCoordinator(
        locationSamples: List<LocationSample>,
        sessionDao: FakeSessionDao,
        journalDao: FakeJournalDao,
    ): SessionCoordinator {
        val orchestrator = CaptureOrchestrator(
            location = LocationCaptureService(FixedLocationDataSource(locationSamples)),
            motion = MotionCaptureService(FixedMotionDataSource(emptyList())),
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

    private fun locationSample(sourceNs: Long): LocationSample = LocationSample(
        latitude = LATITUDE,
        longitude = LONGITUDE,
        accuracyMeters = ACCURACY_M,
        altitudeMeters = null,
        speedMps = null,
        bearingDeg = null,
        sourceTimestampNs = sourceNs,
        receiptUtcMillis = LOC_RECEIPT_MS_BASE + sourceNs,
    )
}

/**
 * Test double for [BundleFinalizer] — kept distinct from the
 * `private class FakeBundleFinalizer` in [FinalizeFromJournalTest] to
 * avoid file-private name collision (Kotlin treats package-level class
 * names uniquely even with `private` visibility).
 */
private class RecoveryFakeBundleFinalizer(
    private val bundle: BundleFinalizer.FinalizedBundle? = null,
    private val error: DomainError? = null,
) : BundleFinalizer {
    var capturedSession: SessionEntity? = null
        private set
    var capturedJournal: SessionJournalEntity? = null
        private set

    override suspend fun finalize(
        session: SessionEntity,
        journal: SessionJournalEntity,
    ): Result<BundleFinalizer.FinalizedBundle, DomainError> {
        capturedSession = session
        capturedJournal = journal
        return when {
            bundle != null -> Result.Ok(bundle)
            error != null -> Result.Err(error)
            else -> error("RecoveryFakeBundleFinalizer needs either bundle or error")
        }
    }
}
