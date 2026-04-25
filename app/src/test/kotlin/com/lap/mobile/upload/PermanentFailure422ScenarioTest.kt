package com.lap.mobile.upload

import com.lap.mobile.data.UploadQueueDao
import com.lap.mobile.data.UploadQueueEntity
import com.lap.schemas.v1.UUID7
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * 422-immediate-failed_permanent scenario (T170). Locks the cross-tick
 * "no retry on client error" UX contract.
 *
 * Differs from existing
 * [UploadOrchestrationTest.marks_failed_permanent_on_permanent_failure_so_no_more_retries_are_attempted]:
 *  - That test fires a single tick and asserts the immediate state.
 *  - This test fires a second tick to lock the *consequence*: after a
 *    PermanentFailure flips the row to `failed_permanent`, the queue
 *    contains no `queued` rows, so the next [UploadOrchestration.runOnce]
 *    returns [UploadOutcome.NothingToDo] and the malformed bundle never
 *    gets re-attempted.
 *
 * This is the failure-mode counterpart to T169 (502 → backoff retry →
 * eventual success). 4xx client errors (validation failures, bad payload,
 * schema mismatches) tell us the bundle itself is the problem; retrying
 * with the same bytes wastes battery + bandwidth + server CPU and would
 * only push the failed_permanent flip out by 10 attempts (T155 cap).
 * Failing fast surfaces the manual-retry CTA (T161) so the user can
 * abandon (T162) or wait for a server-side fix.
 */
private const val SESSION_ID: UUID7 = "01997333-9d2e-7000-8000-000000000012"
private const val IDEMPOTENCY_KEY: String = "$SESSION_ID-key"
private const val CHUNKS_TOTAL: Int = 4
private const val ATTEMPT_BEFORE_FIRST_TICK: Int = 0
private const val PERMANENT_HTTP_CODE: Int = 422
private const val PERMANENT_MESSAGE: String = "validation_error: chunk count mismatch"
private val FIXED_NOW: Instant = Instant.parse("2026-04-25T16:00:00Z")
private val FIXED_CLOCK: Clock = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"))

class PermanentFailure422ScenarioTest {
    @Test
    fun a_422_flips_state_to_failed_permanent_on_the_first_tick_with_attempt_count_incremented_once() = runTest {
        val dao = PermFailFakeQueueDao(initialRow = queuedRow())
        val orchestration = UploadOrchestration(
            queueDao = dao,
            uploader = BundleUploader { _, _ ->
                UploadResult.PermanentFailure(httpCode = PERMANENT_HTTP_CODE, message = PERMANENT_MESSAGE)
            },
            clock = FIXED_CLOCK,
        )

        val outcome = orchestration.runOnce()

        assertEquals(UploadOutcome.PermanentFailure, outcome)
        assertEquals("failed_permanent", dao.currentState)
        assertEquals(ATTEMPT_BEFORE_FIRST_TICK + 1, dao.currentAttemptCount)
        assertEquals(PERMANENT_HTTP_CODE, dao.currentLastHttpCode)
    }

    @Test
    fun after_a_422_a_subsequent_tick_finds_nothing_to_do_so_a_client_error_doesnt_re_cycle() = runTest {
        // Headline T170 invariant. The malformed bundle stays out of the
        // dequeue path on every subsequent tick. Without this, the worker
        // would keep picking the row up; the orchestrator would call the
        // uploader; the uploader would 422 again; we'd waste battery +
        // bandwidth + server CPU and the row would only flip after the
        // T155 10-attempt cap. Failing fast on 4xx is the whole point.
        var uploaderCalls = 0
        val dao = PermFailFakeQueueDao(initialRow = queuedRow())
        val orchestration = UploadOrchestration(
            queueDao = dao,
            uploader = BundleUploader { _, _ ->
                uploaderCalls += 1
                UploadResult.PermanentFailure(httpCode = PERMANENT_HTTP_CODE, message = PERMANENT_MESSAGE)
            },
            clock = FIXED_CLOCK,
        )

        orchestration.runOnce()
        val secondOutcome = orchestration.runOnce()

        assertEquals(UploadOutcome.NothingToDo, secondOutcome)
        assertEquals("uploader must not be called on the second tick", 1, uploaderCalls)
    }

    @Test
    fun a_422_writes_no_next_retry_at_so_the_worker_doesnt_park_on_a_dead_deadline() = runTest {
        val dao = PermFailFakeQueueDao(initialRow = queuedRow())
        val orchestration = UploadOrchestration(
            queueDao = dao,
            uploader = BundleUploader { _, _ ->
                UploadResult.PermanentFailure(httpCode = PERMANENT_HTTP_CODE, message = PERMANENT_MESSAGE)
            },
            clock = FIXED_CLOCK,
        )

        orchestration.runOnce()

        assertNull(
            "PermanentFailure must clear next_retry_at — there's no scheduled retry for a 4xx client error",
            dao.currentNextRetryAt,
        )
    }

    private fun queuedRow(): UploadQueueEntity = UploadQueueEntity(
        sessionId = SESSION_ID,
        state = "queued",
        chunksTotal = CHUNKS_TOTAL,
        idempotencyKey = IDEMPOTENCY_KEY,
        attemptCount = ATTEMPT_BEFORE_FIRST_TICK,
    )
}

private class PermFailFakeQueueDao(initialRow: UploadQueueEntity) : UploadQueueDao {
    private var row: UploadQueueEntity = initialRow

    val currentState: String get() = row.state
    val currentAttemptCount: Int get() = row.attemptCount
    val currentLastHttpCode: Int? get() = row.lastHttpCode
    val currentNextRetryAt: Instant? get() = row.nextRetryAt

    override suspend fun insertOrIgnore(row: UploadQueueEntity): Long =
        error("insertOrIgnore not exercised by PermanentFailure422ScenarioTest")

    override suspend fun dequeueNext(state: String): UploadQueueEntity? = if (row.state == state) row else null

    override suspend fun updateAttempt(
        sessionId: UUID7,
        state: String,
        attemptCount: Int,
        nextRetryAt: Instant?,
        lastHttpCode: Int?,
        lastError: String?,
    ): Int {
        check(sessionId == row.sessionId)
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
