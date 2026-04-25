package com.lap.mobile.upload

import com.lap.mobile.data.UploadQueueEntity

/**
 * Per-session badge variant rendered on the dashboard / session list
 * (T164). Closed set — every session row resolves to exactly one of:
 *
 *  - [Local]: captured locally; no upload work yet (no row in
 *    `upload_queue`). Includes sessions that finished but were never
 *    enqueued and sessions awaiting [com.lap.mobile.upload.IdempotentUploadEnqueuer].
 *  - [Uploading]: queued or in-flight; carries a 0..100 progress
 *    percent so the UI can show "Uploading 42%" without a second
 *    round-trip to the database.
 *  - [CloudConfirmed]: terminal-success — the server acknowledged
 *    receipt and the bytes are safe off-device (T146 completed state).
 *  - [Failed]: terminal-fail — T155's 10-attempt cap tripped or a
 *    non-retryable response arrived. Surfaces the [ManualRetryUseCase]
 *    (T161) and [AbandonUploadUseCase] (T162) CTAs.
 */
sealed interface UploadBadgeState {
    data object Local : UploadBadgeState
    data class Uploading(val progressPercent: Int) : UploadBadgeState {
        init {
            require(progressPercent in 0..MAX_PERCENT) {
                "progressPercent must be 0..$MAX_PERCENT, got $progressPercent"
            }
        }

        private companion object {
            const val MAX_PERCENT: Int = 100
        }
    }
    data object CloudConfirmed : UploadBadgeState
    data object Failed : UploadBadgeState
}

/**
 * Pure-Kotlin resolver: maps a session's upload-side facts to a single
 * [UploadBadgeState] for the UI. Inputs are everything the dashboard
 * already has from a single Room snapshot + the per-session
 * [UploadProgressBus] entry.
 *
 * Live progress wins over persisted [UploadQueueEntity.chunksSent] when
 * present — the bus is updated per chunk-ack by the worker, while the
 * queue row's `chunks_sent` is only persisted at retry/finalize
 * boundaries (T154/T155). Without this rule the badge would flicker
 * back to a stale lower percent every time the dashboard recomposed.
 *
 * Why a kernel: the state-machine mapping IS the spec. Locking it in
 * pure Kotlin means a future state addition (e.g. an explicit
 * "uploading" state separate from "queued", or a "verifying" stage)
 * breaks the resolver's `when` first instead of silently rendering
 * the wrong badge variant.
 */
object UploadBadgeStateResolver {
    private const val STATE_QUEUED: String = "queued"
    private const val STATE_COMPLETED: String = "completed"
    private const val STATE_FAILED_PERMANENT: String = "failed_permanent"
    private const val PERCENT_DENOMINATOR: Int = 100

    fun resolve(queueRow: UploadQueueEntity?, liveProgress: UploadProgress?): UploadBadgeState {
        if (queueRow == null) return UploadBadgeState.Local
        return when (queueRow.state) {
            STATE_QUEUED -> UploadBadgeState.Uploading(progressPercent = computePercent(queueRow, liveProgress))
            STATE_COMPLETED -> UploadBadgeState.CloudConfirmed
            STATE_FAILED_PERMANENT -> UploadBadgeState.Failed
            else -> error("Unknown upload_queue state '${queueRow.state}' for session ${queueRow.sessionId}")
        }
    }

    private fun computePercent(queueRow: UploadQueueEntity, liveProgress: UploadProgress?): Int {
        val sent = liveProgress?.chunksSent ?: queueRow.chunksSent
        val total = liveProgress?.chunksTotal ?: queueRow.chunksTotal
        if (total <= 0) return 0
        return ((sent.toLong() * PERCENT_DENOMINATOR) / total).toInt().coerceIn(0, PERCENT_DENOMINATOR)
    }
}
