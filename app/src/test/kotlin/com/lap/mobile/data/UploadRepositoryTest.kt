package com.lap.mobile.data

import com.lap.mobile.core.DomainError
import com.lap.mobile.core.Result
import com.lap.schemas.v1.UUID7
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private const val SESSION_ID_A: UUID7 = "0194aaaa-aaaa-7aaa-aaaa-aaaaaaaaaaaa"
private const val SESSION_ID_B: UUID7 = "0194bbbb-bbbb-7bbb-bbbb-bbbbbbbbbbbb"
private const val SESSION_ID_MISSING: UUID7 = "0194cccc-cccc-7ccc-cccc-cccccccccccc"
private const val CHUNKS_TOTAL_FIXTURE = 8
private const val ATTEMPT_COUNT_FIXTURE = 3
private const val LAST_HTTP_CODE_FIXTURE = 502
private const val LAST_ERROR_FIXTURE = "bad gateway"
private const val STATE_FAILED = "failed"

/**
 * JVM tests for [UploadRepository] (T227).
 *
 * The repo is a thin Result-mapping layer over [UploadQueueDao] —
 * tests use an inline [FakeUploadQueueDao] that mirrors the
 * production INSERT-OR-IGNORE + dequeue + update semantics so the
 * repo's contracts (duplicate returns sentinel rowid, missing
 * row returns NotFound) are locked end-to-end.
 */
class UploadRepositoryTest {
    @Test
    fun enqueue_returns_ok_with_rowid_on_first_insert() = runTest {
        val dao = FakeUploadQueueDao()
        val repo = UploadRepository(dao)

        val result = repo.enqueue(uploadRow(SESSION_ID_A))

        assertTrue("expected Ok, got $result", result is Result.Ok)
        val rowid = (result as Result.Ok).value
        assertTrue("expected rowid > 0, got $rowid", rowid > 0)
    }

    @Test
    fun enqueue_returns_ok_with_duplicate_sentinel_on_second_insert() = runTest {
        val dao = FakeUploadQueueDao()
        val repo = UploadRepository(dao)
        repo.enqueue(uploadRow(SESSION_ID_A))

        val result = repo.enqueue(uploadRow(SESSION_ID_A))

        assertTrue("expected Ok, got $result", result is Result.Ok)
        assertEquals(UploadRepository.DUPLICATE_ROWID, (result as Result.Ok).value)
    }

    @Test
    fun dequeue_next_returns_ok_null_when_empty() = runTest {
        val dao = FakeUploadQueueDao()
        val repo = UploadRepository(dao)

        val result = repo.dequeueNext()

        assertTrue("expected Ok, got $result", result is Result.Ok)
        assertNull((result as Result.Ok).value)
    }

    @Test
    fun dequeue_next_returns_ok_with_oldest_queued_row_first() = runTest {
        val dao = FakeUploadQueueDao()
        val repo = UploadRepository(dao)
        repo.enqueue(uploadRow(SESSION_ID_A))
        repo.enqueue(uploadRow(SESSION_ID_B))

        val result = repo.dequeueNext()

        assertTrue(result is Result.Ok)
        // UUIDv7 time-ordered: SESSION_ID_A < SESSION_ID_B → A dequeues first.
        assertEquals(SESSION_ID_A, (result as Result.Ok).value?.sessionId)
    }

    @Test
    fun record_attempt_returns_ok_when_row_updated() = runTest {
        val dao = FakeUploadQueueDao()
        val repo = UploadRepository(dao)
        repo.enqueue(uploadRow(SESSION_ID_A))

        val result = repo.recordAttempt(
            sessionId = SESSION_ID_A,
            state = STATE_FAILED,
            attemptCount = ATTEMPT_COUNT_FIXTURE,
            nextRetryAt = Instant.parse("2026-04-25T09:30:00Z"),
            lastHttpCode = LAST_HTTP_CODE_FIXTURE,
            lastError = LAST_ERROR_FIXTURE,
        )

        assertTrue("expected Ok, got $result", result is Result.Ok)
        val row = dao.rows[SESSION_ID_A]
        assertEquals(STATE_FAILED, row?.state)
        assertEquals(ATTEMPT_COUNT_FIXTURE, row?.attemptCount)
        assertEquals(LAST_HTTP_CODE_FIXTURE, row?.lastHttpCode)
        assertEquals(LAST_ERROR_FIXTURE, row?.lastError)
    }

    @Test
    fun record_attempt_returns_not_found_when_row_missing() = runTest {
        val dao = FakeUploadQueueDao()
        val repo = UploadRepository(dao)

        val result = repo.recordAttempt(
            sessionId = SESSION_ID_MISSING,
            state = STATE_FAILED,
            attemptCount = 1,
            nextRetryAt = null,
            lastHttpCode = null,
            lastError = null,
        )

        assertTrue("expected Err, got $result", result is Result.Err)
        val error = (result as Result.Err).error
        assertTrue("expected NotFound, got $error", error is DomainError.NotFound)
    }

    private fun uploadRow(sessionId: UUID7): UploadQueueEntity = UploadQueueEntity(
        sessionId = sessionId,
        state = UploadRepository.STATE_QUEUED,
        chunksTotal = CHUNKS_TOTAL_FIXTURE,
        idempotencyKey = sessionId,
    )

    private class FakeUploadQueueDao : UploadQueueDao {
        val rows: MutableMap<UUID7, UploadQueueEntity> = linkedMapOf()
        private var nextRowid: Long = 1L

        override suspend fun insertOrIgnore(row: UploadQueueEntity): Long {
            if (row.sessionId in rows) {
                return UploadRepository.DUPLICATE_ROWID
            }
            rows[row.sessionId] = row
            return nextRowid++
        }

        override suspend fun dequeueNext(state: String): UploadQueueEntity? = rows.values
            .asSequence()
            .filter { it.state == state }
            .sortedBy { it.sessionId }
            .firstOrNull()

        override suspend fun updateAttempt(
            sessionId: UUID7,
            state: String,
            attemptCount: Int,
            nextRetryAt: Instant?,
            lastHttpCode: Int?,
            lastError: String?,
        ): Int {
            val existing = rows[sessionId] ?: return 0
            rows[sessionId] = existing.copy(
                state = state,
                attemptCount = attemptCount,
                nextRetryAt = nextRetryAt,
                lastHttpCode = lastHttpCode,
                lastError = lastError,
            )
            return 1
        }
    }
}
