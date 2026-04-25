package com.lap.mobile.upload

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager entry point for the upload queue (T147). Thin glue —
 * the orchestration logic ([UploadOrchestration]) stays pure-Kotlin
 * and JVM-tested; this worker just maps the [UploadOutcome] to the
 * appropriate [androidx.work.ListenableWorker.Result] for the
 * runtime.
 *
 * NothingToDo + Success → success() so the worker doesn't fight the
 * scheduler when the queue drains; Retry → retry() so WorkManager
 * applies its backoff (T154 will refine to per-row nextRetryAt
 * scheduling); PermanentFailure → failure() so the worker is
 * removed from the schedule (the row stays at failed_permanent for
 * the user to manually retry per T161).
 */
@HiltWorker
class UploadBundleWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val orchestration: UploadOrchestration,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = when (orchestration.runOnce()) {
        UploadOutcome.NothingToDo, UploadOutcome.Success -> Result.success()
        UploadOutcome.Retry -> Result.retry()
        UploadOutcome.PermanentFailure -> Result.failure()
    }
}
