package com.lap.mobile.upload

import com.lap.mobile.data.UploadQueueDao
import com.lap.mobile.data.UploadQueueEntity
import com.lap.schemas.v1.UUID7
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * End-to-end relaunch-after-force-kill scenario (T168).
 *
 * Failure model differs from [AirplaneModeResumeUploadTest] (T167):
 *  - **T167** simulates an in-process error where [UploadOrchestration]'s
 *    [UploadQueueDao.updateAttempt] runs and persists post-failure state.
 *  - **T168** simulates the OS killing the app mid-attempt — `updateAttempt`
 *    *never runs*, so the row reflects whatever was last persisted *before*
 *    the doomed attempt began (the row was committed at enqueue / a prior
 *    successful chunk-ack).
 *
 * The contract being locked: on relaunch, the orchestrator treats a
 * killed-mid-attempt row identically to a never-attempted one. A fresh
 * dequeue picks it up; the next attempt increments `attempt_count` from
 * its persisted value (NOT from zero — otherwise force-kills would
 * indefinitely defer the T155 cap and a hopeless upload would re-cycle
 * forever after each kill); `chunks_sent` is honoured as the resume
 * cursor; `idempotency_key` is replayed so the server dedups against
 * any partial bytes the killed attempt managed to send.
 *
 * Why this matters: WorkManager persists pending work across process
 * death by design, but the queue row's stability is *our* responsibility.
 * If the orchestrator wrote a "now uploading" intermediate state on
 * dequeue (it doesn't) the kill would leave that state stranded and
 * the next dequeue might skip the row.
 */
private const val SESSION_ID: UUID7 = "01997333-9d2e-7000-8000-000000000010"
private const val IDEMPOTENCY_KEY: String = "$SESSION_ID-key"
private const val CHUNKS_TOTAL: Int = 8
private const val CHUNKS_SENT_BEFORE_KILL: Int = 5
private const val ATTEMPT_COUNT_BEFORE_KILL: Int = 2
private const val SUCCESS_HTTP_CODE: Int = 201
private val FIXED_NOW: Instant = Instant.parse("2026-04-25T15:00:00Z")
private val FIXED_CLOCK: Clock = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"))

class ForceKillResumeUploadTest {
    @Test
    fun relaunch_picks_up_in_flight_row_and_lands_completed_so_user_doesnt_lose_progress() = runTest {
        val dao = KillReplayFakeQueueDao(
            initialRow = preKillRow(),
        )
        val orchestration = UploadOrchestration(
            queueDao = dao,
            uploader = BundleUploader { _, _ -> UploadResult.Success(httpCode = SUCCESS_HTTP_CODE) },
            clock = FIXED_CLOCK,
        )

        val outcome = orchestration.runOnce()

        assertEquals(UploadOutcome.Success, outcome)
        assertEquals("completed", dao.currentState)
    }

    @Test
    fun attempt_count_increments_from_pre_kill_value_so_t155_cap_keeps_counting_after_relaunch() {
        // If the orchestrator reset attempt_count on dequeue (or zeroed it
        // because the row "looked fresh"), a hopeless upload could re-cycle
        // forever — every force-kill would defer the T155 10-attempt cap.
        // Locking the increment-from-persisted-value here prevents that
        // regression.
        runTest {
            val dao = KillReplayFakeQueueDao(initialRow = preKillRow())
            val orchestration = UploadOrchestration(
                queueDao = dao,
                uploader = BundleUploader { _, _ -> UploadResult.Success(httpCode = SUCCESS_HTTP_CODE) },
                clock = FIXED_CLOCK,
            )

            orchestration.runOnce()

            assertEquals(ATTEMPT_COUNT_BEFORE_KILL + 1, dao.currentAttemptCount)
        }
    }

    @Test
    fun chunks_sent_resume_cursor_survives_the_kill_so_next_attempt_doesnt_re_upload_acked_chunks() {
        // The killed attempt sent some chunks the server accepted; their
        // count is on the row. The next attempt must honour that cursor —
        // the orchestrator's updateAttempt deliberately doesn't touch
        // chunks_sent (T152), so a successful relaunch attempt leaves the
        // cursor at its pre-kill value (the new chunk-ack accounting is
        // BundleMultipartBuilder's job, not the orchestration's).
        runTest {
            val dao = KillReplayFakeQueueDao(initialRow = preKillRow())
            val orchestration = UploadOrchestration(
                queueDao = dao,
                uploader = BundleUploader { _, _ -> UploadResult.Success(httpCode = SUCCESS_HTTP_CODE) },
                clock = FIXED_CLOCK,
            )

            orchestration.runOnce()

            assertEquals(CHUNKS_SENT_BEFORE_KILL, dao.currentChunksSent)
        }
    }

    @Test
    fun idempotency_key_replayed_so_server_dedups_against_partial_bytes_from_the_killed_attempt() = runTest {
        var observedKey: String? = null
        val orchestration = UploadOrchestration(
            queueDao = KillReplayFakeQueueDao(initialRow = preKillRow()),
            uploader = BundleUploader { _, idempotencyKey ->
                observedKey = idempotencyKey
                UploadResult.Success(httpCode = SUCCESS_HTTP_CODE)
            },
            clock = FIXED_CLOCK,
        )

        orchestration.runOnce()

        assertEquals(IDEMPOTENCY_KEY, observedKey)
    }

    private fun preKillRow(): UploadQueueEntity = UploadQueueEntity(
        sessionId = SESSION_ID,
        state = "queued",
        chunksTotal = CHUNKS_TOTAL,
        idempotencyKey = IDEMPOTENCY_KEY,
        attemptCount = ATTEMPT_COUNT_BEFORE_KILL,
        chunksSent = CHUNKS_SENT_BEFORE_KILL,
    )
}

private class KillReplayFakeQueueDao(initialRow: UploadQueueEntity) : UploadQueueDao {
    private var row: UploadQueueEntity = initialRow

    val currentState: String get() = row.state
    val currentAttemptCount: Int get() = row.attemptCount
    val currentChunksSent: Int get() = row.chunksSent

    override suspend fun insertOrIgnore(row: UploadQueueEntity): Long =
        error("insertOrIgnore not exercised by ForceKillResumeUploadTest")

    override suspend fun dequeueNext(state: String): UploadQueueEntity? = if (row.state == state) row else null

    override suspend fun updateAttempt(
        sessionId: UUID7,
        state: String,
        attemptCount: Int,
        nextRetryAt: Instant?,
        lastHttpCode: Int?,
        lastError: String?,
    ): Int {
        check(sessionId == row.sessionId) {
            "updateAttempt for unknown sessionId $sessionId (have ${row.sessionId})"
        }
        // Mirror UploadQueueDao.updateAttempt @Query: only the five named
        // columns are touched. chunks_sent + idempotency_key + chunks_total
        // + session_id stay unchanged — that's what makes the relaunch
        // resume contract hold for both T167 and T168 scenarios.
        row = row.copy(
            state = state,
            attemptCount = attemptCount,
            nextRetryAt = nextRetryAt,
            lastHttpCode = lastHttpCode,
            lastError = lastError,
        )
        return 1
    }
}
