package com.lap.mobile.data

import com.lap.schemas.v1.SourceTag
import com.lap.schemas.v1.UUID7
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * Companion to [JournalReconciler] (T055) and [OrphanTempFileReconciler]
 * (T056). This one works off SessionEntity.state: any row sitting in
 * `recording` or `finalizing` at app launch means the app process died
 * between state transitions and the user should be offered recovery
 * (T058 modal → T059 Finalize-from-journal) per ADR-012.
 *
 * Completed/failed sessions are never recoverable by definition —
 * their state is terminal — so they're excluded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CrashedSessionReconcilerTest {
    private val idA: UUID7 = "0194aaaa-aaaa-7aaa-aaaa-aaaaaaaaaaa1"
    private val idB: UUID7 = "0194bbbb-bbbb-7bbb-bbbb-bbbbbbbbbbbb"
    private val idC: UUID7 = "0194cccc-cccc-7ccc-cccc-cccccccccccc"
    private val idD: UUID7 = "0194dddd-dddd-7ddd-dddd-dddddddddddd"
    private val idE: UUID7 = "0194eeee-eeee-7eee-eeee-eeeeeeeeeeee"

    @Test
    fun empty_db_reports_no_recoverable_sessions() = runTest {
        val reconciler = CrashedSessionReconciler(FakeSessionDao())
        assertEquals(emptyList<SessionEntity>(), reconciler.findRecoverable())
    }

    @Test
    fun recording_sessions_are_recoverable() = runTest {
        val dao = FakeSessionDao().apply {
            put(session(idA, state = "recording"))
        }
        val reconciler = CrashedSessionReconciler(dao)
        assertEquals(listOf(idA), reconciler.findRecoverable().map { it.id })
    }

    @Test
    fun finalizing_sessions_are_recoverable() = runTest {
        val dao = FakeSessionDao().apply {
            put(session(idB, state = "finalizing"))
        }
        val reconciler = CrashedSessionReconciler(dao)
        assertEquals(listOf(idB), reconciler.findRecoverable().map { it.id })
    }

    @Test
    fun completed_and_failed_sessions_are_not_recoverable() = runTest {
        val dao = FakeSessionDao().apply {
            put(session(idA, state = "completed"))
            put(session(idB, state = "failed"))
            put(session(idC, state = "idle"))
            put(session(idD, state = "armed"))
        }
        val reconciler = CrashedSessionReconciler(dao)
        assertEquals(emptyList<SessionEntity>(), reconciler.findRecoverable())
    }

    @Test
    fun mixed_states_return_only_recording_and_finalizing() = runTest {
        val dao = FakeSessionDao().apply {
            put(session(idA, state = "recording"))
            put(session(idB, state = "finalizing"))
            put(session(idC, state = "completed"))
            put(session(idD, state = "failed"))
            put(session(idE, state = "idle"))
        }
        val reconciler = CrashedSessionReconciler(dao)
        val recoverable = reconciler.findRecoverable().map { it.id }.toSet()
        assertEquals(setOf(idA, idB), recoverable)
    }

    private fun session(id: UUID7, state: String): SessionEntity = SessionEntity(
        id = id,
        state = state,
        source = SourceTag.REAL,
        startedAt = Instant.parse("2026-04-24T09:00:00Z"),
    )
}
