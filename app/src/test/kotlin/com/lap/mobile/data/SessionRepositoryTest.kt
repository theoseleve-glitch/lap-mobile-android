package com.lap.mobile.data

import com.lap.mobile.core.DomainError
import com.lap.mobile.core.Result
import com.lap.schemas.v1.SourceTag
import com.lap.schemas.v1.UUID7
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SESSION_ID_A: UUID7 = "0194aaaa-aaaa-7aaa-aaaa-aaaaaaaaaaaa"
private const val SESSION_ID_B: UUID7 = "0194bbbb-bbbb-7bbb-bbbb-bbbbbbbbbbbb"
private const val SESSION_ID_MISSING: UUID7 = "0194cccc-cccc-7ccc-cccc-cccccccccccc"
private const val DAO_ERROR_MESSAGE = "fake-disk-failure"

/**
 * JVM tests for [SessionRepository] (T227).
 *
 * The repo is a thin Result-mapping layer over [SessionDao] — tests
 * exercise the mapping/error contract per method using either the
 * existing [FakeSessionDao] (for the suspend methods that don't
 * need flow plumbing) or a flow-aware inline fake (for
 * observeByState).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryTest {
    @Test
    fun observe_by_state_emits_ok_with_dao_rows() = runTest {
        val source = MutableStateFlow<List<SessionEntity>>(
            listOf(sessionEntity(SESSION_ID_A, RecordingState.COMPLETED)),
        )
        val dao = FlowSourceFakeSessionDao(source)
        val repo = SessionRepository(dao)

        val first = repo.observeByState(RecordingState.COMPLETED).first()

        assertTrue("expected Ok, got $first", first is Result.Ok)
        val rows = (first as Result.Ok).value
        assertEquals(1, rows.size)
        assertEquals(SESSION_ID_A, rows.single().id)
    }

    @Test
    fun observe_by_state_maps_thrown_exception_to_io_err_emission() = runTest {
        val dao = ThrowingFlowSessionDao()
        val repo = SessionRepository(dao)

        val first = repo.observeByState(RecordingState.COMPLETED).first()

        assertTrue("expected Err, got $first", first is Result.Err)
        val error = (first as Result.Err).error
        assertTrue("expected DomainError.Io, got $error", error is DomainError.Io)
        assertEquals(DAO_ERROR_MESSAGE, (error as DomainError.Io).message)
    }

    @Test
    fun find_by_id_returns_ok_with_entity_when_present() = runTest {
        val dao = FakeSessionDao().apply { put(sessionEntity(SESSION_ID_A, RecordingState.RECORDING)) }
        val repo = SessionRepository(dao)

        val result = repo.findById(SESSION_ID_A)

        assertTrue(result is Result.Ok)
        val entity = (result as Result.Ok).value
        assertEquals(SESSION_ID_A, entity?.id)
    }

    @Test
    fun find_by_id_returns_ok_null_when_missing() = runTest {
        val dao = FakeSessionDao()
        val repo = SessionRepository(dao)

        val result = repo.findById(SESSION_ID_MISSING)

        assertTrue(result is Result.Ok)
        assertNull((result as Result.Ok).value)
    }

    @Test
    fun delete_returns_ok_when_row_existed() = runTest {
        val dao = FakeSessionDao().apply { put(sessionEntity(SESSION_ID_A, RecordingState.COMPLETED)) }
        val repo = SessionRepository(dao)

        val result = repo.delete(SESSION_ID_A)

        assertTrue("expected Ok, got $result", result is Result.Ok)
    }

    @Test
    fun delete_returns_not_found_when_row_missing() = runTest {
        val dao = FakeSessionDao()
        val repo = SessionRepository(dao)

        val result = repo.delete(SESSION_ID_MISSING)

        assertTrue("expected Err, got $result", result is Result.Err)
        val error = (result as Result.Err).error
        assertTrue("expected NotFound, got $error", error is DomainError.NotFound)
    }

    @Test
    fun observe_by_state_filter_only_emits_matching_state() = runTest {
        val source = MutableStateFlow<List<SessionEntity>>(
            listOf(
                sessionEntity(SESSION_ID_A, RecordingState.COMPLETED),
                sessionEntity(SESSION_ID_B, RecordingState.RECORDING),
            ),
        )
        val dao = FlowSourceFakeSessionDao(source)
        val repo = SessionRepository(dao)

        val completed = repo.observeByState(RecordingState.COMPLETED).first()
        val recording = repo.observeByState(RecordingState.RECORDING).first()

        val completedRows = (completed as Result.Ok).value
        val recordingRows = (recording as Result.Ok).value
        assertEquals(1, completedRows.size)
        assertEquals(SESSION_ID_A, completedRows.single().id)
        assertEquals(1, recordingRows.size)
        assertEquals(SESSION_ID_B, recordingRows.single().id)
    }

    private fun sessionEntity(id: UUID7, state: RecordingState): SessionEntity = SessionEntity(
        id = id,
        state = state.wireValue,
        source = SourceTag.REAL,
    )

    /**
     * Inline fake that drives [SessionDao.observeByStatus] from a
     * [MutableStateFlow] so tests can verify the repository's
     * Flow-mapping behavior without a Room runtime. Filters by
     * the state argument so a single source flow can serve
     * multiple state subscriptions.
     */
    private class FlowSourceFakeSessionDao(
        private val source: MutableStateFlow<List<SessionEntity>>,
    ) : SessionDao {
        override suspend fun upsert(session: SessionEntity) = error("not used")
        override suspend fun update(session: SessionEntity): Int = error("not used")
        override suspend fun listByStatus(state: String): List<SessionEntity> = error("not used")
        override suspend fun findById(id: UUID7): SessionEntity? = error("not used")
        override suspend fun deleteById(id: UUID7): Int = error("not used")

        override fun observeByStatus(state: String): Flow<List<SessionEntity>> =
            source.map { rows -> rows.filter { it.state == state } }
    }

    /**
     * Inline fake whose observeByStatus emits a single throwing
     * value to verify [SessionRepository.observeByState]'s
     * `.catch` mapping to [DomainError.Io].
     */
    private class ThrowingFlowSessionDao : SessionDao {
        override suspend fun upsert(session: SessionEntity) = error("not used")
        override suspend fun update(session: SessionEntity): Int = error("not used")
        override suspend fun listByStatus(state: String): List<SessionEntity> = error("not used")
        override suspend fun findById(id: UUID7): SessionEntity? = error("not used")
        override suspend fun deleteById(id: UUID7): Int = error("not used")

        override fun observeByStatus(state: String): Flow<List<SessionEntity>> = flow {
            throw IllegalStateException(DAO_ERROR_MESSAGE)
        }
    }
}
