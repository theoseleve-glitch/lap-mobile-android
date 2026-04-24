package com.lap.mobile.data

import com.lap.mobile.core.DomainError
import com.lap.mobile.core.Result
import com.lap.schemas.v1.SourceTag
import com.lap.schemas.v1.UUID7
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private const val FIXTURE_SAMPLE_COUNT: Int = 27_000

@OptIn(ExperimentalCoroutinesApi::class)
class FinalizeFromJournalTest {
    private val sessionId: UUID7 = "0194aaaa-aaaa-7aaa-aaaa-aaaaaaaaaaaa"
    private val startedAt: Instant = Instant.parse("2026-04-24T09:00:00Z")
    private val endedAt: Instant = Instant.parse("2026-04-24T09:45:00Z")

    @Test
    fun success_path_transitions_session_to_completed_and_stamps_metadata() = runTest {
        val sessionDao = FakeSessionDao().apply {
            put(session(state = "recording"))
        }
        val journalDao = FakeJournalDao().apply {
            put(journal(sessionId))
        }
        val finalizer = FakeBundleFinalizer(
            BundleFinalizer.FinalizedBundle(
                endedAt = endedAt,
                fileUri = "file:///bundles/$sessionId.bundle",
                sampleCount = FIXTURE_SAMPLE_COUNT,
            ),
        )
        val useCase = FinalizeFromJournal(sessionDao, journalDao, finalizer)

        val result = useCase.run(sessionId)

        assertTrue("expected Ok, got $result", result is Result.Ok)
        val row = sessionDao.rows.getValue(sessionId)
        assertEquals("completed", row.state)
        assertEquals(endedAt, row.endedAt)
        assertEquals("file:///bundles/$sessionId.bundle", row.fileUri)
        assertEquals(FIXTURE_SAMPLE_COUNT, row.sampleCount)
    }

    @Test
    fun missing_session_returns_not_found_without_touching_dao() = runTest {
        val sessionDao = FakeSessionDao()
        val journalDao = FakeJournalDao().apply { put(journal(sessionId)) }
        val useCase = FinalizeFromJournal(
            sessionDao,
            journalDao,
            FakeBundleFinalizer(neverCalled = true),
        )

        val result = useCase.run(sessionId)

        assertTrue("expected Err, got $result", result is Result.Err)
        val err = (result as Result.Err).error
        assertTrue("expected NotFound, got $err", err is DomainError.NotFound)
        assertTrue(
            "no session writes on NotFound",
            sessionDao.rows.isEmpty(),
        )
    }

    @Test
    fun missing_journal_returns_not_found_without_mutating_session() = runTest {
        val original = session(state = "recording")
        val sessionDao = FakeSessionDao().apply { put(original) }
        val journalDao = FakeJournalDao()
        val useCase = FinalizeFromJournal(
            sessionDao,
            journalDao,
            FakeBundleFinalizer(neverCalled = true),
        )

        val result = useCase.run(sessionId)

        assertTrue("expected Err, got $result", result is Result.Err)
        assertTrue(
            "expected NotFound, got ${(result as Result.Err).error}",
            result.error is DomainError.NotFound,
        )
        assertEquals(
            "session row must not change when journal is missing",
            original,
            sessionDao.rows.getValue(sessionId),
        )
    }

    @Test
    fun finalizer_failure_transitions_session_to_failed_and_propagates_error() = runTest {
        val sessionDao = FakeSessionDao().apply { put(session(state = "finalizing")) }
        val journalDao = FakeJournalDao().apply { put(journal(sessionId)) }
        val finalizer = FakeBundleFinalizer(
            error = DomainError.Io(message = "checksum mismatch"),
        )
        val useCase = FinalizeFromJournal(sessionDao, journalDao, finalizer)

        val result = useCase.run(sessionId)

        assertTrue("expected Err, got $result", result is Result.Err)
        assertEquals(
            DomainError.Io(message = "checksum mismatch"),
            (result as Result.Err).error,
        )
        assertEquals("failed", sessionDao.rows.getValue(sessionId).state)
    }

    @Test
    fun finalizer_is_called_with_the_loaded_session_and_journal() = runTest {
        val sessionDao = FakeSessionDao().apply { put(session(state = "recording")) }
        val journal = journal(sessionId)
        val journalDao = FakeJournalDao().apply { put(journal) }
        val finalizer = FakeBundleFinalizer(
            BundleFinalizer.FinalizedBundle(
                endedAt = endedAt,
                fileUri = "x",
                sampleCount = 1,
            ),
        )
        val useCase = FinalizeFromJournal(sessionDao, journalDao, finalizer)

        useCase.run(sessionId)

        assertNotNull("finalizer must be called on success path", finalizer.capturedSession)
        assertEquals(sessionId, finalizer.capturedSession?.id)
        assertEquals(journal, finalizer.capturedJournal)
    }

    private fun session(state: String): SessionEntity = SessionEntity(
        id = sessionId,
        state = state,
        source = SourceTag.REAL,
        startedAt = startedAt,
    )

    private fun journal(sessionId: UUID7): SessionJournalEntity = SessionJournalEntity(
        sessionId = sessionId,
        lastHeartbeatAt = startedAt.plusSeconds(30L),
        status = "recording",
    )
}

/**
 * Test double for [BundleFinalizer] — returns a fixed result and records
 * the arguments it was called with. Set [neverCalled] to `true` in tests
 * that expect the use case to short-circuit before invoking the
 * finalizer.
 */
private class FakeBundleFinalizer(
    private val bundle: BundleFinalizer.FinalizedBundle? = null,
    private val error: DomainError? = null,
    private val neverCalled: Boolean = false,
) : BundleFinalizer {
    var capturedSession: SessionEntity? = null
        private set
    var capturedJournal: SessionJournalEntity? = null
        private set

    override suspend fun finalize(
        session: SessionEntity,
        journal: SessionJournalEntity,
    ): Result<BundleFinalizer.FinalizedBundle, DomainError> {
        check(!neverCalled) { "finalize() should not have been called" }
        capturedSession = session
        capturedJournal = journal
        return when {
            bundle != null -> Result.Ok(bundle)
            error != null -> Result.Err(error)
            else -> error("FakeBundleFinalizer needs either bundle or error")
        }
    }
}
