package com.lap.mobile.upload

import com.lap.mobile.data.UploadQueueEntity
import com.lap.schemas.v1.UUID7
import org.junit.Assert.assertEquals
import org.junit.Test

private const val SESSION_ID: UUID7 = "01997333-9d2e-7000-8000-00000000000e"
private const val IDEMPOTENCY_KEY: String = "$SESSION_ID-key"
private const val CHUNKS_TOTAL: Int = 8
private const val CHUNKS_SENT_QUARTER: Int = 2
private const val CHUNKS_SENT_HALF: Int = 4
private const val EXPECTED_PERCENT_QUARTER: Int = 25
private const val EXPECTED_PERCENT_HALF: Int = 50
private const val FAILED_HTTP_CODE: Int = 502
private const val FAILED_ATTEMPT_COUNT: Int = 10

class UploadBadgeStateResolverTest {
    @Test
    fun a_session_with_no_queue_row_resolves_to_local_so_freshly_captured_sessions_show_as_unsynced() {
        val state = UploadBadgeStateResolver.resolve(queueRow = null, liveProgress = null)

        assertEquals(UploadBadgeState.Local, state)
    }

    @Test
    fun a_queued_row_with_no_live_progress_resolves_to_uploading_using_persisted_chunks_sent() {
        // Worker hasn't yet emitted live progress for this session
        // (e.g., dashboard opened during a quiet stretch). Falling back
        // to the queue row's chunks_sent gives a stable %, even if a
        // few seconds stale, instead of a flicker to 0%.
        val row = queuedRow(chunksSent = CHUNKS_SENT_QUARTER)

        val state = UploadBadgeStateResolver.resolve(queueRow = row, liveProgress = null)

        assertEquals(UploadBadgeState.Uploading(progressPercent = EXPECTED_PERCENT_QUARTER), state)
    }

    @Test
    fun a_queued_row_with_live_progress_resolves_to_uploading_using_live_chunks_sent_so_ui_doesnt_lag_db() {
        // Live progress wins because [UploadProgressBus] is updated per
        // chunk acknowledgment by the worker, while [UploadQueueEntity]
        // chunks_sent is only persisted at retry/finalize boundaries.
        val row = queuedRow(chunksSent = CHUNKS_SENT_QUARTER)
        val live = UploadProgress(
            sessionId = SESSION_ID,
            chunksSent = CHUNKS_SENT_HALF,
            chunksTotal = CHUNKS_TOTAL,
        )

        val state = UploadBadgeStateResolver.resolve(queueRow = row, liveProgress = live)

        assertEquals(UploadBadgeState.Uploading(progressPercent = EXPECTED_PERCENT_HALF), state)
    }

    @Test
    fun a_completed_row_resolves_to_cloud_confirmed_so_user_sees_session_is_safe_off_device() {
        val row = queuedRow(chunksSent = CHUNKS_TOTAL).copy(state = "completed")

        val state = UploadBadgeStateResolver.resolve(queueRow = row, liveProgress = null)

        assertEquals(UploadBadgeState.CloudConfirmed, state)
    }

    @Test
    fun a_failed_permanent_row_resolves_to_failed_so_t161_retry_cta_can_surface() {
        val row = queuedRow(chunksSent = CHUNKS_SENT_QUARTER).copy(
            state = "failed_permanent",
            attemptCount = FAILED_ATTEMPT_COUNT,
            lastHttpCode = FAILED_HTTP_CODE,
        )

        val state = UploadBadgeStateResolver.resolve(queueRow = row, liveProgress = null)

        assertEquals(UploadBadgeState.Failed, state)
    }

    @Test
    fun chunks_total_zero_resolves_to_zero_percent_uploading_without_dividing_by_zero() {
        // Edge case: a freshly enqueued row before the multipart builder
        // has computed chunksTotal (or a degenerate empty bundle). Must
        // not crash with ArithmeticException — render 0% and let the
        // worker push the row forward.
        val row = queuedRow(chunksSent = 0).copy(chunksTotal = 0)

        val state = UploadBadgeStateResolver.resolve(queueRow = row, liveProgress = null)

        assertEquals(UploadBadgeState.Uploading(progressPercent = 0), state)
    }

    private fun queuedRow(chunksSent: Int): UploadQueueEntity = UploadQueueEntity(
        sessionId = SESSION_ID,
        state = "queued",
        chunksTotal = CHUNKS_TOTAL,
        idempotencyKey = IDEMPOTENCY_KEY,
        chunksSent = chunksSent,
    )
}
