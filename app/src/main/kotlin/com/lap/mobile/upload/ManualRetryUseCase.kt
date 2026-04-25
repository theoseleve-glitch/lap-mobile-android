package com.lap.mobile.upload

import com.lap.mobile.data.UploadQueueDao
import com.lap.schemas.v1.UUID7
import javax.inject.Inject

/**
 * Outcome of a user-initiated retry against the upload queue.
 *
 * - [ReQueued]: the row existed and was reset back to `state = "queued"`
 *   with a fresh attempt_count = 0; the worker will pick it up on the
 *   next dequeue (T160 ordering kernel).
 * - [NotFound]: no row matched the given session_id. Most likely the
 *   session was concurrently abandoned (T162) or already completed.
 *   Surfacing this lets the caller warn the user instead of silently
 *   no-op'ing a tap on a stale dashboard entry.
 */
enum class ManualRetryOutcome { ReQueued, NotFound }

/**
 * Hand-resets a single upload queue row so the worker re-attempts it.
 *
 * Triggered by the user tapping "Retry" on a dashboard entry that
 * T155 flipped to `failed_permanent` after hitting the 10-attempt
 * cap (or, more rarely, on a still-`queued` row the user wants to
 * unblock without waiting for the next dequeue tick).
 *
 * What we reset:
 *  - **state → "queued"**: undoes the T155 cap so [UploadQueueOrdering]
 *    will consider the row again.
 *  - **attempt_count → 0**: the cap counts attempts since enqueue;
 *    leaving it at 10 would re-trip T155 on the very next failure.
 *  - **next_retry_at → null**: a stale BackoffSchedule timestamp
 *    (T154) would otherwise park the worker on a dead deadline.
 *  - **last_http_code / last_error → null**: prevents the dashboard
 *    from showing a stale failure alongside a fresh queued row.
 *
 * What we deliberately preserve:
 *  - **idempotency_key** (T150): the server still dedups partial
 *    uploads from earlier attempts via this key, so reusing it makes
 *    the retry safe to repeat.
 *  - **chunks_sent** (T152): the resume cursor lets the next attempt
 *    start from where the last one left off instead of re-uploading
 *    chunks the server already accepted.
 *
 * Both preservations happen by *not* writing those columns — the
 * DAO's UPDATE only touches the five fields above.
 */
class ManualRetryUseCase @Inject constructor(
    private val queueDao: UploadQueueDao,
) {
    suspend fun retry(sessionId: UUID7): ManualRetryOutcome {
        val rowsUpdated = queueDao.updateAttempt(
            sessionId = sessionId,
            state = STATE_QUEUED,
            attemptCount = ATTEMPT_COUNT_RESET,
            nextRetryAt = null,
            lastHttpCode = null,
            lastError = null,
        )
        return if (rowsUpdated > 0) ManualRetryOutcome.ReQueued else ManualRetryOutcome.NotFound
    }

    private companion object {
        const val STATE_QUEUED: String = "queued"
        const val ATTEMPT_COUNT_RESET: Int = 0
    }
}
