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

private const val SESSION_ID: UUID7 = "01997333-9d2e-7000-8000-000000000001"
private const val IDEMPOTENCY_KEY: String = "01997333-9d2e-7000-8000-000000000001"
private const val SUCCESS_HTTP: Int = 201
private const val RETRY_HTTP: Int = 502
private const val PERMANENT_HTTP: Int = 422
private const val ATTEMPT_BEFORE: Int = 1
private val FIXED_NOW: Instant = Instant.parse("2026-04-25T14:30:00Z")
private val FIXED_CLOCK: Clock = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"))

class UploadOrchestrationTest {
    @Test
    fun returns_done_when_queue_empty() = runTest {
        val orchestration = UploadOrchestration(
            queueDao = FakeQueueDao(rows = emptyList()),
            uploader = BundleUploader { _, _ -> error("must not call uploader when queue empty") },
            clock = FIXED_CLOCK,
        )

        assertEquals(UploadOutcome.NothingToDo, orchestration.runOnce())
    }

    @Test
    fun calls_uploader_with_session_id_and_idempotency_key() = runTest {
        val seen = mutableListOf<Pair<String, String>>()
        val orchestration = UploadOrchestration(
            queueDao = FakeQueueDao(rows = listOf(queuedRow())),
            uploader = BundleUploader { sessionId, idempotencyKey ->
                seen.add(sessionId to idempotencyKey)
                UploadResult.Success(httpCode = SUCCESS_HTTP)
            },
            clock = FIXED_CLOCK,
        )

        orchestration.runOnce()

        assertEquals(listOf(SESSION_ID to IDEMPOTENCY_KEY), seen)
    }

    @Test
    fun marks_completed_on_success_with_recorded_http_code() = runTest {
        val dao = FakeQueueDao(rows = listOf(queuedRow()))
        val orchestration = UploadOrchestration(
            queueDao = dao,
            uploader = BundleUploader { _, _ -> UploadResult.Success(httpCode = SUCCESS_HTTP) },
            clock = FIXED_CLOCK,
        )

        val outcome = orchestration.runOnce()

        assertEquals(UploadOutcome.Success, outcome)
        val update = requireNotNull(dao.lastUpdate)
        assertEquals("completed", update.state)
        assertEquals(ATTEMPT_BEFORE + 1, update.attemptCount)
        assertEquals(SUCCESS_HTTP, update.lastHttpCode)
        assertNull(update.lastError)
        assertNull(update.nextRetryAt)
    }

    @Test
    fun returns_retry_on_retryable_failure_and_keeps_state_queued_with_attempt_increment() = runTest {
        val dao = FakeQueueDao(rows = listOf(queuedRow()))
        val orchestration = UploadOrchestration(
            queueDao = dao,
            uploader = BundleUploader { _, _ ->
                UploadResult.Retryable(httpCode = RETRY_HTTP, message = "bad gateway")
            },
            clock = FIXED_CLOCK,
        )

        val outcome = orchestration.runOnce()

        assertEquals(UploadOutcome.Retry, outcome)
        val update = requireNotNull(dao.lastUpdate)
        // queued (not failed_permanent) — server-side will likely succeed on retry.
        assertEquals("queued", update.state)
        assertEquals(ATTEMPT_BEFORE + 1, update.attemptCount)
        assertEquals(RETRY_HTTP, update.lastHttpCode)
        assertEquals("bad gateway", update.lastError)
    }

    @Test
    fun marks_failed_permanent_on_permanent_failure_so_no_more_retries_are_attempted() = runTest {
        val dao = FakeQueueDao(rows = listOf(queuedRow()))
        val orchestration = UploadOrchestration(
            queueDao = dao,
            uploader = BundleUploader { _, _ ->
                UploadResult.PermanentFailure(httpCode = PERMANENT_HTTP, message = "validation_error")
            },
            clock = FIXED_CLOCK,
        )

        val outcome = orchestration.runOnce()

        assertEquals(UploadOutcome.PermanentFailure, outcome)
        val update = requireNotNull(dao.lastUpdate)
        assertEquals("failed_permanent", update.state)
        assertEquals(PERMANENT_HTTP, update.lastHttpCode)
        assertEquals("validation_error", update.lastError)
        assertNull("permanent failure must clear nextRetryAt", update.nextRetryAt)
    }

    private fun queuedRow(): UploadQueueEntity = UploadQueueEntity(
        sessionId = SESSION_ID,
        state = "queued",
        chunksTotal = 1,
        idempotencyKey = IDEMPOTENCY_KEY,
        attemptCount = ATTEMPT_BEFORE,
    )
}

private class FakeQueueDao(private val rows: List<UploadQueueEntity>) : UploadQueueDao {
    var lastUpdate: AttemptUpdate? = null

    override suspend fun insertOrIgnore(row: UploadQueueEntity): Long = error("not used in this test")

    override suspend fun dequeueNext(state: String): UploadQueueEntity? = rows.firstOrNull { it.state == state }

    override suspend fun updateAttempt(
        sessionId: UUID7,
        state: String,
        attemptCount: Int,
        nextRetryAt: Instant?,
        lastHttpCode: Int?,
        lastError: String?,
    ): Int {
        lastUpdate = AttemptUpdate(sessionId, state, attemptCount, nextRetryAt, lastHttpCode, lastError)
        return 1
    }
}

private data class AttemptUpdate(
    val sessionId: UUID7,
    val state: String,
    val attemptCount: Int,
    val nextRetryAt: Instant?,
    val lastHttpCode: Int?,
    val lastError: String?,
)
