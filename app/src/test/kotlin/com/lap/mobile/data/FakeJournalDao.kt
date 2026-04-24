package com.lap.mobile.data

import com.lap.schemas.v1.UUID7
import java.time.Instant

/**
 * Shared in-memory [JournalDao] test double. Lives in the test source
 * set so both [JournalWriterTest] and [JournalReconcilerTest] (and
 * any later test that exercises the journal layer) reuse the same
 * behavior — the production Room-backed DAO doesn't come into play
 * until instrumented tests (T083+).
 */
internal class FakeJournalDao : JournalDao {
    val rows: MutableMap<UUID7, SessionJournalEntity> = linkedMapOf()
    var upsertCalls: Int = 0
        private set

    fun put(row: SessionJournalEntity) {
        rows[row.sessionId] = row
    }

    override suspend fun upsertHeartbeat(entry: SessionJournalEntity) {
        rows[entry.sessionId] = entry
        upsertCalls++
    }

    override suspend fun listStale(before: Instant): List<SessionJournalEntity> =
        rows.values.filter { it.lastHeartbeatAt.isBefore(before) }

    override suspend fun findBySessionId(sessionId: UUID7): SessionJournalEntity? = rows[sessionId]
}
