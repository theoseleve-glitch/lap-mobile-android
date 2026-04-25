package com.lap.mobile.upload

import com.lap.mobile.data.UploadQueueDao
import com.lap.mobile.data.UploadQueueEntity
import com.lap.schemas.v1.UUID7
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

private const val SESSION_A: UUID7 = "01997333-9d2e-7000-8000-00000000000a"
private const val SESSION_B: UUID7 = "01997333-9d2e-7000-8000-00000000000b"
private const val CHUNKS_TOTAL: Int = 4
private const val NOT_A_FRESH_INSERT: Long = -1L

class IdempotentUploadEnqueuerTest {
    @Test
    fun first_enqueue_writes_a_queued_row_with_idempotency_key_equal_to_session_id() = runTest {
        val dao = FakeUploadQueueDao()
        val enqueuer = IdempotentUploadEnqueuer(dao)

        enqueuer.enqueue(sessionId = SESSION_A, chunksTotal = CHUNKS_TOTAL)

        val persisted = requireNotNull(dao.rows[SESSION_A]) { "row was not inserted" }
        // ADR-012: server dedupes uploads on Idempotency-Key. Reusing the session UUIDv7
        // means the network retry replays the exact key the server first saw.
        assertEquals(SESSION_A, persisted.idempotencyKey)
    }

    @Test
    fun first_enqueue_initializes_state_to_queued_with_zero_progress_and_zero_attempts() = runTest {
        val dao = FakeUploadQueueDao()
        val enqueuer = IdempotentUploadEnqueuer(dao)

        enqueuer.enqueue(sessionId = SESSION_A, chunksTotal = CHUNKS_TOTAL)

        val persisted = requireNotNull(dao.rows[SESSION_A])
        assertEquals("queued", persisted.state)
        assertEquals(CHUNKS_TOTAL, persisted.chunksTotal)
        assertEquals(0, persisted.chunksSent)
        assertEquals(0, persisted.attemptCount)
    }

    @Test
    fun first_enqueue_returns_created() = runTest {
        val dao = FakeUploadQueueDao()
        val enqueuer = IdempotentUploadEnqueuer(dao)

        val outcome = enqueuer.enqueue(sessionId = SESSION_A, chunksTotal = CHUNKS_TOTAL)

        assertEquals(EnqueueOutcome.Created, outcome)
    }

    @Test
    fun re_enqueueing_the_same_session_returns_already_queued() = runTest {
        val dao = FakeUploadQueueDao()
        val enqueuer = IdempotentUploadEnqueuer(dao)

        enqueuer.enqueue(sessionId = SESSION_A, chunksTotal = CHUNKS_TOTAL)
        val outcome = enqueuer.enqueue(sessionId = SESSION_A, chunksTotal = CHUNKS_TOTAL)

        assertEquals(EnqueueOutcome.AlreadyQueued, outcome)
    }

    @Test
    fun re_enqueueing_does_not_clobber_the_persisted_row_so_idempotency_key_and_progress_survive() = runTest {
        // Simulate the worker having advanced the upload (chunks_sent + attempt_count).
        // A re-enqueue (e.g., user retries while the worker is parked) MUST NOT reset
        // those columns or the next attempt would re-upload chunks the server already
        // accepted.
        val dao = FakeUploadQueueDao()
        val enqueuer = IdempotentUploadEnqueuer(dao)
        enqueuer.enqueue(sessionId = SESSION_A, chunksTotal = CHUNKS_TOTAL)
        val persistedAfterFirst = requireNotNull(dao.rows[SESSION_A])
        dao.rows[SESSION_A] = persistedAfterFirst.copy(chunksSent = 2, attemptCount = 1)

        enqueuer.enqueue(sessionId = SESSION_A, chunksTotal = CHUNKS_TOTAL)

        val persistedAfterSecond = requireNotNull(dao.rows[SESSION_A])
        assertEquals(SESSION_A, persistedAfterSecond.idempotencyKey)
        assertEquals(2, persistedAfterSecond.chunksSent)
        assertEquals(1, persistedAfterSecond.attemptCount)
    }

    @Test
    fun two_different_sessions_each_get_their_own_row_with_their_own_key() = runTest {
        val dao = FakeUploadQueueDao()
        val enqueuer = IdempotentUploadEnqueuer(dao)

        enqueuer.enqueue(sessionId = SESSION_A, chunksTotal = CHUNKS_TOTAL)
        enqueuer.enqueue(sessionId = SESSION_B, chunksTotal = CHUNKS_TOTAL)

        assertEquals(SESSION_A, dao.rows.getValue(SESSION_A).idempotencyKey)
        assertEquals(SESSION_B, dao.rows.getValue(SESSION_B).idempotencyKey)
    }
}

/**
 * Simulates SQLite `INSERT OR IGNORE`: returns the rowid for a fresh
 * insert and -1L when a row for the same primary key already exists.
 * Mirrors what Room's `@Insert(onConflict = OnConflictStrategy.IGNORE)`
 * returns at runtime — the contract the production [UploadQueueDao]
 * lives up to.
 */
private class FakeUploadQueueDao : UploadQueueDao {
    val rows: MutableMap<UUID7, UploadQueueEntity> = mutableMapOf()
    private var nextRowId: Long = 1L

    override suspend fun insertOrIgnore(row: UploadQueueEntity): Long {
        if (row.sessionId in rows) return NOT_A_FRESH_INSERT
        rows[row.sessionId] = row
        return nextRowId++
    }

    override suspend fun dequeueNext(state: String): UploadQueueEntity? =
        error("dequeueNext not exercised by enqueuer tests")

    override suspend fun updateAttempt(
        sessionId: UUID7,
        state: String,
        attemptCount: Int,
        nextRetryAt: Instant?,
        lastHttpCode: Int?,
        lastError: String?,
    ): Int = error("updateAttempt not exercised by enqueuer tests")
}
