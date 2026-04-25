package com.lap.mobile.data

import com.lap.schemas.v1.UUID7
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val SESSION_OLDEST: UUID7 = "01997333-9d2e-7000-8000-000000000001"
private const val SESSION_MIDDLE: UUID7 = "01997333-9d2e-7000-8000-000000000002"
private const val SESSION_NEWEST: UUID7 = "01997333-9d2e-7000-8000-000000000003"
private const val IDEMPOTENCY_KEY_PREFIX: String = "01997333-9d2e-7000-8000-00000000000"
private const val CHUNKS_TOTAL_DEFAULT: Int = 1

class UploadQueueOrderingTest {
    @Test
    fun empty_input_returns_null_so_the_worker_reports_nothing_to_do() {
        assertNull(UploadQueueOrdering.selectNextDequeueable(rows = emptyList()))
    }

    @Test
    fun a_single_queued_row_is_returned_unchanged() {
        val row = queuedRow(SESSION_OLDEST)

        assertEquals(row, UploadQueueOrdering.selectNextDequeueable(rows = listOf(row)))
    }

    @Test
    fun two_queued_rows_returns_the_one_with_the_lower_session_id_so_uuidv7_ordering_yields_fifo() {
        // UUIDv7's first 48 bits are a Unix-ms timestamp, so a smaller
        // session_id was enqueued first. Locking ASC ordering is what
        // makes the DAO query "FIFO by enqueued_at" without needing a
        // separate enqueued_at column.
        val older = queuedRow(SESSION_OLDEST)
        val newer = queuedRow(SESSION_NEWEST)

        // Pass the rows in reverse so we don't accidentally test "first in list".
        val picked = UploadQueueOrdering.selectNextDequeueable(rows = listOf(newer, older))

        assertEquals(older, picked)
    }

    @Test
    fun a_failed_permanent_row_is_skipped_so_a_dead_upload_does_not_block_younger_queued_work() {
        // Even though the failed_permanent row has the lower session_id
        // (was enqueued first), it must be skipped — its T155 cap was
        // already hit and the user has to manually retry it (T161).
        val deadOldest = queuedRow(SESSION_OLDEST).copy(state = "failed_permanent")
        val newerQueued = queuedRow(SESSION_NEWEST)

        val picked = UploadQueueOrdering.selectNextDequeueable(rows = listOf(deadOldest, newerQueued))

        assertEquals(newerQueued, picked)
    }

    @Test
    fun a_completed_row_is_skipped_so_a_one_shot_success_does_not_get_re_dequeued() {
        val completed = queuedRow(SESSION_OLDEST).copy(state = "completed")
        val newerQueued = queuedRow(SESSION_NEWEST)

        val picked = UploadQueueOrdering.selectNextDequeueable(rows = listOf(completed, newerQueued))

        assertEquals(newerQueued, picked)
    }

    @Test
    fun all_rows_in_terminal_states_returns_null_so_the_worker_idles() {
        // Worker has nothing to do when the queue contains only finished
        // (completed) or dead (failed_permanent) rows.
        val rows = listOf(
            queuedRow(SESSION_OLDEST).copy(state = "completed"),
            queuedRow(SESSION_NEWEST).copy(state = "failed_permanent"),
        )

        assertNull(UploadQueueOrdering.selectNextDequeueable(rows = rows))
    }

    @Test
    fun mixed_states_picks_the_oldest_queued_row_ignoring_anything_in_a_terminal_state() {
        val rows = listOf(
            queuedRow(SESSION_NEWEST),
            queuedRow(SESSION_OLDEST).copy(state = "failed_permanent"),
            queuedRow(SESSION_MIDDLE),
        )

        val picked = UploadQueueOrdering.selectNextDequeueable(rows = rows)

        assertEquals(queuedRow(SESSION_MIDDLE), picked)
    }

    private fun queuedRow(sessionId: UUID7): UploadQueueEntity = UploadQueueEntity(
        sessionId = sessionId,
        state = "queued",
        chunksTotal = CHUNKS_TOTAL_DEFAULT,
        idempotencyKey = "$IDEMPOTENCY_KEY_PREFIX${sessionId.takeLast(1)}",
    )
}
