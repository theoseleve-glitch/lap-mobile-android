package com.lap.mobile.upload

import com.lap.mobile.data.UploadQueueDao
import java.time.Clock

private const val STATE_QUEUED: String = "queued"
private const val STATE_COMPLETED: String = "completed"
private const val STATE_FAILED_PERMANENT: String = "failed_permanent"

/**
 * Outcome of one HTTP attempt by [BundleUploader] (T147 + T148).
 *  - [Success] — server accepted the bundle (typically 2xx).
 *  - [Retryable] — transient (5xx, 408, network) — caller schedules
 *    a retry per the T154 backoff curve.
 *  - [PermanentFailure] — client error (4xx other than auth/timeout)
 *    — bundle is malformed or rejected; no retry will help, surface
 *    to user so they can investigate or abandon.
 */
sealed interface UploadResult {
    data class Success(val httpCode: Int) : UploadResult
    data class Retryable(val httpCode: Int?, val message: String) : UploadResult
    data class PermanentFailure(val httpCode: Int, val message: String) : UploadResult
}

/**
 * Single-attempt upload seam (T147). Lets the orchestration stay
 * pure-Kotlin / JVM-testable while the actual HTTP work
 * ([com.lap.mobile.upload.IngestApi], T148+) lives behind the
 * fun-interface and gets exercised by the integration tests.
 */
fun interface BundleUploader {
    suspend fun upload(sessionId: String, idempotencyKey: String): UploadResult
}

/**
 * Aggregated outcome of one orchestration tick (T147). The worker
 * (UploadBundleWorker) maps these to ListenableWorker.Result —
 * NothingToDo + Success → Result.success, Retry → Result.retry,
 * PermanentFailure → Result.failure. Splitting the outcome from the
 * worker.Result lets the orchestration stay JVM-testable without
 * Robolectric.
 */
enum class UploadOutcome { NothingToDo, Success, Retry, PermanentFailure }

/**
 * Pure orchestration of one upload attempt (T147). One tick:
 *   1. dequeue next "queued" row from [UploadQueueDao] (FIFO via
 *      UUIDv7 ordering per T160).
 *   2. delegate to [BundleUploader] for the actual HTTP work.
 *   3. update the queue row state + attempt count + last error / http
 *      code based on the [UploadResult].
 *
 * Returns an [UploadOutcome] the worker maps to a WorkManager Result
 * for the runtime. The [clock] dep is held for upcoming T154 backoff
 * computation (next_retry_at = now + delay) — currently unused, but
 * pinning it now keeps the worker DI graph stable across the next
 * few tasks.
 */
class UploadOrchestration @javax.inject.Inject constructor(
    private val queueDao: UploadQueueDao,
    private val uploader: BundleUploader,
    @Suppress("UnusedPrivateProperty") private val clock: Clock,
) {
    suspend fun runOnce(): UploadOutcome {
        val row = queueDao.dequeueNext() ?: return UploadOutcome.NothingToDo
        val result = uploader.upload(row.sessionId, row.idempotencyKey)
        val nextAttempt = row.attemptCount + 1
        return when (result) {
            is UploadResult.Success -> {
                queueDao.updateAttempt(
                    sessionId = row.sessionId,
                    state = STATE_COMPLETED,
                    attemptCount = nextAttempt,
                    nextRetryAt = null,
                    lastHttpCode = result.httpCode,
                    lastError = null,
                )
                UploadOutcome.Success
            }
            is UploadResult.Retryable -> {
                queueDao.updateAttempt(
                    sessionId = row.sessionId,
                    state = STATE_QUEUED,
                    attemptCount = nextAttempt,
                    nextRetryAt = null,
                    lastHttpCode = result.httpCode,
                    lastError = result.message,
                )
                UploadOutcome.Retry
            }
            is UploadResult.PermanentFailure -> {
                queueDao.updateAttempt(
                    sessionId = row.sessionId,
                    state = STATE_FAILED_PERMANENT,
                    attemptCount = nextAttempt,
                    nextRetryAt = null,
                    lastHttpCode = result.httpCode,
                    lastError = result.message,
                )
                UploadOutcome.PermanentFailure
            }
        }
    }
}
