package com.lap.mobile.upload

import com.lap.mobile.data.UploadQueueEntity

/**
 * Bucketed view of [com.lap.mobile.data.UploadQueueEntity] rows for the
 * Uploads screen (T163). Holds the two lists the UI renders side-by-side.
 *
 * Both buckets are session_id ASC — UUIDv7 is time-ordered so this is
 * "oldest first," matching [com.lap.mobile.data.UploadQueueOrdering]'s
 * dequeue policy. Same ordering rule across both buckets keeps the two
 * lists in the UI feeling coherent.
 */
data class UploadsScreenState(
    val active: List<UploadQueueEntity>,
    val failed: List<UploadQueueEntity>,
) {
    companion object {
        val EMPTY: UploadsScreenState = UploadsScreenState(active = emptyList(), failed = emptyList())
    }
}

/**
 * Pure-Kotlin partition of the upload_queue rows the UI cares about.
 *
 * Visibility rules:
 *  - **Active** = `state == "queued"` — work the worker will pick up
 *    on the next dequeue tick. Drives the "Uploading…" section.
 *  - **Failed** = `state == "failed_permanent"` — T155's 10-attempt
 *    cap has tripped; needs a [ManualRetryUseCase] tap (T161) or an
 *    [AbandonUploadUseCase] tap (T162) from the user.
 *  - **Completed** = `state == "completed"` — dropped from both
 *    buckets. Already done, no UX. Kept in the table only as an
 *    audit trail (and so [com.lap.mobile.upload.IdempotentUploadEnqueuer]
 *    can short-circuit a re-enqueue of the same session).
 *
 * Why a kernel and not just inline filtering in the ViewModel: the
 * partition rules ARE the UI contract. Locking them in pure Kotlin
 * means a future state-machine change (adding "uploading" as its own
 * state, splitting "failed_permanent" into "failed_4xx" /
 * "failed_5xx_capped", etc.) breaks here first instead of silently
 * sliding rows into or out of the wrong bucket on the dashboard.
 */
object UploadsScreenPartition {
    private const val STATE_QUEUED: String = "queued"
    private const val STATE_FAILED_PERMANENT: String = "failed_permanent"

    fun partition(rows: List<UploadQueueEntity>): UploadsScreenState {
        val active = rows.filter { it.state == STATE_QUEUED }.sortedBy { it.sessionId }
        val failed = rows.filter { it.state == STATE_FAILED_PERMANENT }.sortedBy { it.sessionId }
        return UploadsScreenState(active = active, failed = failed)
    }
}
