package com.lap.mobile.data

import com.lap.mobile.core.DomainError
import com.lap.mobile.core.Result
import com.lap.schemas.v1.UUID7
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin repository wrapping [UploadQueueDao] (T227 scaffold).
 *
 * Per CLAUDE.md "Every repository returns Result<T, DomainError>":
 * every method returns [Result]. The DAO operations themselves
 * already encode their idempotency contract (insertOrIgnore returns
 * `-1L` on duplicate session_id) — this repo preserves that
 * contract by exposing the rowid as `Long` rather than throwing or
 * returning a synthetic NotFound; callers distinguish duplicate
 * (rowid == [DUPLICATE_ROWID]) from successful insert (rowid > 0).
 *
 * The repo deliberately doesn't own backoff scheduling or chunk-
 * count tracking — those policies live in
 * [com.lap.mobile.upload.UploadOrchestration] (T155) +
 * [com.lap.mobile.upload.IdempotentUploadEnqueuer] (T150) which
 * call the DAO directly today. Future tasks may route those
 * through the repo for unified error mapping; this scaffold
 * locks the API shape without forcing a pre-existing-call-site
 * refactor.
 */
@Singleton
class UploadRepository
@Inject
constructor(
    private val uploadQueueDao: UploadQueueDao,
) {
    /**
     * Insert a queue row, idempotent on `session_id`. Returns the
     * new rowid on a fresh insert, or [DUPLICATE_ROWID] when a row
     * for the same session already exists — the duplicate case is
     * still [Result.Ok] because re-enqueueing a session that's
     * already queued is the application's success path (no-op),
     * not an error.
     */
    suspend fun enqueue(row: UploadQueueEntity): Result<Long, DomainError> = runCatchingDao {
        uploadQueueDao.insertOrIgnore(row)
    }

    /**
     * Dequeue the next row in the given [state] (defaults to
     * "queued"). Returns `Ok(null)` when the queue is empty in
     * that state — the worker (T147) treats null as "nothing to
     * do" and returns `Result.success()` to WorkManager.
     */
    suspend fun dequeueNext(state: String = STATE_QUEUED): Result<UploadQueueEntity?, DomainError> = runCatchingDao {
        uploadQueueDao.dequeueNext(state)
    }

    /**
     * Record an attempt outcome for a session — state transition,
     * attempt counter increment, retry scheduling. Returns
     * [DomainError.NotFound] when no row was matched (concurrent
     * abandon swept the row out from under the worker).
     */
    @Suppress("LongParameterList")
    suspend fun recordAttempt(
        sessionId: UUID7,
        state: String,
        attemptCount: Int,
        nextRetryAt: Instant?,
        lastHttpCode: Int?,
        lastError: String?,
    ): Result<Unit, DomainError> = runCatchingDao {
        val updated = uploadQueueDao.updateAttempt(
            sessionId = sessionId,
            state = state,
            attemptCount = attemptCount,
            nextRetryAt = nextRetryAt,
            lastHttpCode = lastHttpCode,
            lastError = lastError,
        )
        if (updated == 0) {
            return Result.Err(DomainError.NotFound("upload_queue:$sessionId"))
        }
    }

    companion object {
        /** Sentinel rowid returned by SQLite's `INSERT OR IGNORE` on a duplicate. */
        const val DUPLICATE_ROWID: Long = -1L

        /** Wire-format value for the "queued" upload state — mirrors the DAO default. */
        const val STATE_QUEUED: String = "queued"
    }
}

@Suppress("TooGenericExceptionCaught")
private inline fun <T> runCatchingDao(block: () -> T): Result<T, DomainError> {
    return try {
        Result.Ok(block())
    } catch (e: Exception) {
        Result.Err(DomainError.Io(e.message.orEmpty()))
    }
}
