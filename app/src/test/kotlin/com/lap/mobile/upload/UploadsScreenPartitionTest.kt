package com.lap.mobile.upload

import com.lap.mobile.data.UploadQueueEntity
import com.lap.schemas.v1.UUID7
import org.junit.Assert.assertEquals
import org.junit.Test

private const val SESSION_OLDEST: UUID7 = "01997333-9d2e-7000-8000-000000000001"
private const val SESSION_MIDDLE: UUID7 = "01997333-9d2e-7000-8000-000000000002"
private const val SESSION_NEWEST: UUID7 = "01997333-9d2e-7000-8000-000000000003"
private const val IDEMPOTENCY_KEY_PREFIX: String = "01997333-9d2e-7000-8000-00000000000"
private const val CHUNKS_TOTAL_DEFAULT: Int = 4

class UploadsScreenPartitionTest {
    @Test
    fun empty_input_returns_empty_active_and_empty_failed_buckets() {
        val state = UploadsScreenPartition.partition(rows = emptyList())

        assertEquals(emptyList<UploadQueueEntity>(), state.active)
        assertEquals(emptyList<UploadQueueEntity>(), state.failed)
    }

    @Test
    fun a_queued_row_lands_in_the_active_bucket_so_the_user_sees_in_flight_work() {
        val row = row(SESSION_OLDEST, state = "queued")

        val state = UploadsScreenPartition.partition(rows = listOf(row))

        assertEquals(listOf(row), state.active)
        assertEquals(emptyList<UploadQueueEntity>(), state.failed)
    }

    @Test
    fun a_failed_permanent_row_lands_in_the_failed_bucket_so_the_user_sees_what_needs_a_retry_or_abandon() {
        val row = row(SESSION_OLDEST, state = "failed_permanent")

        val state = UploadsScreenPartition.partition(rows = listOf(row))

        assertEquals(emptyList<UploadQueueEntity>(), state.active)
        assertEquals(listOf(row), state.failed)
    }

    @Test
    fun a_completed_row_is_excluded_from_both_buckets_so_the_dashboard_doesnt_clutter_with_done_uploads() {
        // T146 marks rows "completed" once the server confirms the bundle.
        // Those rows are kept for an audit trail but the active+failed
        // dashboard is for *actionable* work — a completed row has no UX.
        val row = row(SESSION_OLDEST, state = "completed")

        val state = UploadsScreenPartition.partition(rows = listOf(row))

        assertEquals(emptyList<UploadQueueEntity>(), state.active)
        assertEquals(emptyList<UploadQueueEntity>(), state.failed)
    }

    @Test
    fun active_bucket_is_session_id_ascending_so_ordering_matches_t160_dequeue_fifo() {
        // UUIDv7 first 48 bits are unix-ms, so session_id ASC == enqueue order.
        // Surfacing rows in the same order the worker will pick them lets the
        // user predict "what will upload next" from what they see at the top.
        val older = row(SESSION_OLDEST, state = "queued")
        val newer = row(SESSION_NEWEST, state = "queued")

        val state = UploadsScreenPartition.partition(rows = listOf(newer, older))

        assertEquals(listOf(older, newer), state.active)
    }

    @Test
    fun failed_bucket_is_session_id_ascending_so_repeat_failures_have_a_stable_order() {
        // Stable, deterministic ordering — same rule as the active bucket
        // so two side-by-side lists in the UI feel coherent.
        val older = row(SESSION_OLDEST, state = "failed_permanent")
        val newer = row(SESSION_NEWEST, state = "failed_permanent")

        val state = UploadsScreenPartition.partition(rows = listOf(newer, older))

        assertEquals(listOf(older, newer), state.failed)
    }

    @Test
    fun mixed_states_are_partitioned_so_each_row_lands_in_exactly_one_visible_bucket_or_neither() {
        val activeOldest = row(SESSION_OLDEST, state = "queued")
        val failedMiddle = row(SESSION_MIDDLE, state = "failed_permanent")
        val completedNewest = row(SESSION_NEWEST, state = "completed")

        val state = UploadsScreenPartition.partition(
            rows = listOf(completedNewest, failedMiddle, activeOldest),
        )

        assertEquals(listOf(activeOldest), state.active)
        assertEquals(listOf(failedMiddle), state.failed)
    }

    private fun row(sessionId: UUID7, state: String): UploadQueueEntity = UploadQueueEntity(
        sessionId = sessionId,
        state = state,
        chunksTotal = CHUNKS_TOTAL_DEFAULT,
        idempotencyKey = "$IDEMPOTENCY_KEY_PREFIX${sessionId.takeLast(1)}",
    )
}
