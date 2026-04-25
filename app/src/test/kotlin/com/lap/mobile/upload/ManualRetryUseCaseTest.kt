package com.lap.mobile.upload

import com.lap.mobile.data.UploadQueueDao
import com.lap.mobile.data.UploadQueueEntity
import com.lap.schemas.v1.UUID7
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

private const val SESSION_TARGET: UUID7 = "01997333-9d2e-7000-8000-00000000000a"
private const val SESSION_OTHER: UUID7 = "01997333-9d2e-7000-8000-00000000000b"

class ManualRetryUseCaseTest {
    @Test
    fun retry_sets_state_back_to_queued_so_the_worker_picks_up_a_failed_permanent_row_again() = runTest {
        val dao = RetryFakeQueueDao(rowExists = true)

        ManualRetryUseCase(dao).retry(SESSION_TARGET)

        assertEquals("queued", dao.lastUpdate?.state)
    }

    @Test
    fun retry_resets_attempt_count_to_zero_so_the_t155_cap_does_not_trip_on_the_first_re_attempt() = runTest {
        val dao = RetryFakeQueueDao(rowExists = true)

        ManualRetryUseCase(dao).retry(SESSION_TARGET)

        assertEquals(0, dao.lastUpdate?.attemptCount)
    }

    @Test
    fun retry_clears_last_http_code_so_dashboard_doesnt_show_stale_error_alongside_fresh_queued_row() = runTest {
        val dao = RetryFakeQueueDao(rowExists = true)

        ManualRetryUseCase(dao).retry(SESSION_TARGET)

        assertNull(dao.lastUpdate?.lastHttpCode)
    }

    @Test
    fun retry_clears_last_error_message() = runTest {
        val dao = RetryFakeQueueDao(rowExists = true)

        ManualRetryUseCase(dao).retry(SESSION_TARGET)

        assertNull(dao.lastUpdate?.lastError)
    }

    @Test
    fun retry_clears_next_retry_at_so_the_worker_does_not_park_on_a_stale_back_off_timestamp() = runTest {
        val dao = RetryFakeQueueDao(rowExists = true)

        ManualRetryUseCase(dao).retry(SESSION_TARGET)

        assertNull(dao.lastUpdate?.nextRetryAt)
    }

    @Test
    fun retry_targets_the_session_passed_in_so_the_dao_does_not_touch_other_rows() = runTest {
        val dao = RetryFakeQueueDao(rowExists = true)

        ManualRetryUseCase(dao).retry(SESSION_TARGET)

        assertEquals(SESSION_TARGET, dao.lastUpdate?.sessionId)
    }

    @Test
    fun retry_returns_re_queued_when_the_dao_reports_a_row_was_actually_updated() = runTest {
        val dao = RetryFakeQueueDao(rowExists = true)

        val outcome = ManualRetryUseCase(dao).retry(SESSION_TARGET)

        assertEquals(ManualRetryOutcome.ReQueued, outcome)
    }

    @Test
    fun retry_returns_not_found_when_no_row_matched_so_caller_can_warn_user_session_was_already_swept() = runTest {
        // T162 abandon-upload removes rows. If the user taps Retry on a
        // dashboard entry that was concurrently abandoned, surface that
        // the row is gone rather than silently no-op.
        val dao = RetryFakeQueueDao(rowExists = false)

        val outcome = ManualRetryUseCase(dao).retry(SESSION_OTHER)

        assertEquals(ManualRetryOutcome.NotFound, outcome)
    }
}

private data class RetryCapturedUpdate(
    val sessionId: UUID7,
    val state: String,
    val attemptCount: Int,
    val nextRetryAt: Instant?,
    val lastHttpCode: Int?,
    val lastError: String?,
)

private class RetryFakeQueueDao(private val rowExists: Boolean) : UploadQueueDao {
    var lastUpdate: RetryCapturedUpdate? = null

    override suspend fun insertOrIgnore(row: UploadQueueEntity): Long =
        error("insertOrIgnore not exercised by ManualRetryUseCase tests")

    override suspend fun dequeueNext(state: String): UploadQueueEntity? =
        error("dequeueNext not exercised by ManualRetryUseCase tests")

    override suspend fun updateAttempt(
        sessionId: UUID7,
        state: String,
        attemptCount: Int,
        nextRetryAt: Instant?,
        lastHttpCode: Int?,
        lastError: String?,
    ): Int {
        lastUpdate = RetryCapturedUpdate(sessionId, state, attemptCount, nextRetryAt, lastHttpCode, lastError)
        return if (rowExists) 1 else 0
    }
}
