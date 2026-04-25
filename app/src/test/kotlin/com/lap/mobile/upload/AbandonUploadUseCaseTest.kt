package com.lap.mobile.upload

import com.lap.mobile.data.SessionDao
import com.lap.mobile.data.SessionEntity
import com.lap.schemas.v1.UUID7
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val SESSION_TARGET: UUID7 = "01997333-9d2e-7000-8000-00000000000c"
private const val SESSION_OTHER: UUID7 = "01997333-9d2e-7000-8000-00000000000d"

class AbandonUploadUseCaseTest {
    @Test
    fun abandon_deletes_the_targeted_session_row_so_fk_cascade_sweeps_its_upload_queue_row() = runTest {
        val dao = AbandonFakeSessionDao(rowExists = true)

        AbandonUploadUseCase(dao).abandon(SESSION_TARGET)

        assertEquals(SESSION_TARGET, dao.lastDeletedId)
    }

    @Test
    fun abandon_returns_removed_when_the_dao_reports_a_row_was_actually_deleted() = runTest {
        val dao = AbandonFakeSessionDao(rowExists = true)

        val outcome = AbandonUploadUseCase(dao).abandon(SESSION_TARGET)

        assertEquals(AbandonOutcome.Removed, outcome)
    }

    @Test
    fun abandon_returns_not_found_when_no_row_matched_so_caller_can_warn_user_session_already_swept() = runTest {
        // Concurrency: another flow (crash-safe finalizer, server-side
        // sweep, or a duplicate tap) may have removed the row between
        // the user opening the dashboard and tapping Abandon. Surface
        // the gap rather than silently no-op'ing a destructive action.
        val dao = AbandonFakeSessionDao(rowExists = false)

        val outcome = AbandonUploadUseCase(dao).abandon(SESSION_OTHER)

        assertEquals(AbandonOutcome.NotFound, outcome)
    }
}

private class AbandonFakeSessionDao(private val rowExists: Boolean) : SessionDao {
    var lastDeletedId: UUID7? = null

    override suspend fun upsert(session: SessionEntity) = error("upsert not exercised by AbandonUploadUseCase tests")

    override suspend fun update(session: SessionEntity): Int =
        error("update not exercised by AbandonUploadUseCase tests")

    override suspend fun listByStatus(state: String): List<SessionEntity> =
        error("listByStatus not exercised by AbandonUploadUseCase tests")

    override fun observeByStatus(state: String): Flow<List<SessionEntity>> =
        error("observeByStatus not exercised by AbandonUploadUseCase tests")

    override suspend fun findById(id: UUID7): SessionEntity? =
        error("findById not exercised by AbandonUploadUseCase tests")

    override suspend fun deleteById(id: UUID7): Int {
        lastDeletedId = id
        return if (rowExists) 1 else 0
    }
}
