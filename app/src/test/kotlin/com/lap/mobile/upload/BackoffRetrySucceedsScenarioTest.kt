package com.lap.mobile.upload

import com.lap.mobile.data.UploadQueueDao
import com.lap.mobile.data.UploadQueueEntity
import com.lap.schemas.v1.UUID7
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * 502-then-success scenario (T169). Wires together [BackoffSchedule]
 * (T154) + [UploadOrchestration]'s Retryable branch (T155) and locks
 * the end-to-end "transient 5xx → backed-off retry → eventual success"
 * UX contract.
 *
 * Differs from [AirplaneModeResumeUploadTest] (T167) in two ways:
 *  - Asserts `next_retry_at` is actually set on the Retryable update
 *    (T167 only asserted state stays queued + chunks_sent preserved).
 *  - Three ticks instead of two — proves the scheduler can chain
 *    multiple Retryable responses without flipping to failed_permanent
 *    before the cap.
 *
 * The next_retry_at value is checked as a *range* against the
 * BackoffSchedule curve rather than an exact value: the schedule
 * adds 0..25% jitter (T154) so the actual instant is non-deterministic
 * but bounded. Range assertions match the schedule's published
 * contract instead of leaking implementation details about the random
 * seed used internally.
 */
private const val SESSION_ID: UUID7 = "01997333-9d2e-7000-8000-000000000011"
private const val IDEMPOTENCY_KEY: String = "$SESSION_ID-key"
private const val CHUNKS_TOTAL: Int = 4
private const val ATTEMPT_BEFORE_FIRST_TICK: Int = 1
private const val RETRY_HTTP_CODE: Int = 502
private const val RETRY_MESSAGE: String = "Bad Gateway"
private const val SUCCESS_HTTP_CODE: Int = 201
private const val BASE_DELAY_FOR_ATTEMPT_2_MS: Long = 2_000L
private const val JITTER_NUMERATOR: Long = 1L
private const val JITTER_DENOMINATOR: Long = 4L
private val FIXED_NOW: Instant = Instant.parse("2026-04-25T15:30:00Z")
private val FIXED_CLOCK: Clock = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"))

class BackoffRetrySucceedsScenarioTest {
    @Test
    fun a_502_retryable_writes_a_next_retry_at_on_the_curve_so_worker_doesnt_re_fire_immediately() = runTest {
        // Without next_retry_at populated, WorkManager would re-enqueue
        // on the very next constraint poll instead of pacing per
        // BackoffSchedule. Locking this prevents the orchestrator from
        // regressing back to the pre-T169 "always null" assignment.
        val dao = BackoffFakeQueueDao(initialRow = queuedRow())
        val orchestration = UploadOrchestration(
            queueDao = dao,
            uploader = BundleUploader { _, _ ->
                UploadResult.Retryable(httpCode = RETRY_HTTP_CODE, message = RETRY_MESSAGE)
            },
            clock = FIXED_CLOCK,
        )

        orchestration.runOnce()

        val nextRetryAt = requireNotNull(dao.currentNextRetryAt) {
            "next_retry_at must be set on a Retryable so the worker waits the backoff curve"
        }
        val actualDelayMs = Duration.between(FIXED_NOW, nextRetryAt).toMillis()
        val maxJitterMs = BASE_DELAY_FOR_ATTEMPT_2_MS * JITTER_NUMERATOR / JITTER_DENOMINATOR
        assertTrue(
            "delay ${actualDelayMs}ms must be >= base ${BASE_DELAY_FOR_ATTEMPT_2_MS}ms",
            actualDelayMs >= BASE_DELAY_FOR_ATTEMPT_2_MS,
        )
        assertTrue(
            "delay ${actualDelayMs}ms must be <= base + jitter ${BASE_DELAY_FOR_ATTEMPT_2_MS + maxJitterMs}ms",
            actualDelayMs <= BASE_DELAY_FOR_ATTEMPT_2_MS + maxJitterMs,
        )
    }

    @Test
    fun three_ticks_two_retryable_then_success_lands_completed_so_a_transient_5xx_storm_doesnt_block_user() = runTest {
        val dao = BackoffFakeQueueDao(initialRow = queuedRow())
        val uploader = BackoffScriptedUploader(
            scripted = listOf(
                UploadResult.Retryable(httpCode = RETRY_HTTP_CODE, message = RETRY_MESSAGE),
                UploadResult.Retryable(httpCode = RETRY_HTTP_CODE, message = RETRY_MESSAGE),
                UploadResult.Success(httpCode = SUCCESS_HTTP_CODE),
            ),
        )
        val orchestration = UploadOrchestration(queueDao = dao, uploader = uploader, clock = FIXED_CLOCK)

        val outcomes = listOf(
            orchestration.runOnce(),
            orchestration.runOnce(),
            orchestration.runOnce(),
        )

        assertEquals(
            listOf(UploadOutcome.Retry, UploadOutcome.Retry, UploadOutcome.Success),
            outcomes,
        )
        assertEquals("completed", dao.currentState)
    }

    private fun queuedRow(): UploadQueueEntity = UploadQueueEntity(
        sessionId = SESSION_ID,
        state = "queued",
        chunksTotal = CHUNKS_TOTAL,
        idempotencyKey = IDEMPOTENCY_KEY,
        attemptCount = ATTEMPT_BEFORE_FIRST_TICK,
    )
}

private class BackoffScriptedUploader(scripted: List<UploadResult>) : BundleUploader {
    private val script = scripted.toMutableList()

    override suspend fun upload(sessionId: String, idempotencyKey: String): UploadResult {
        check(script.isNotEmpty()) { "BackoffScriptedUploader called more times than scripted" }
        return script.removeFirst()
    }
}

private class BackoffFakeQueueDao(initialRow: UploadQueueEntity) : UploadQueueDao {
    private var row: UploadQueueEntity = initialRow

    val currentState: String get() = row.state
    val currentNextRetryAt: Instant? get() = row.nextRetryAt

    override suspend fun insertOrIgnore(row: UploadQueueEntity): Long =
        error("insertOrIgnore not exercised by BackoffRetrySucceedsScenarioTest")

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
