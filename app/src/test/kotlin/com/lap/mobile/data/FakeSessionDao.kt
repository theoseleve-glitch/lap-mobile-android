package com.lap.mobile.data

import com.lap.schemas.v1.UUID7
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Shared in-memory [SessionDao] test double. Lives in the test source
 * set so reconciler tests (T055–T057) and any later test that
 * exercises the session layer reuse the same behavior — the production
 * Room-backed DAO is deferred to instrumented tests (T083+).
 *
 * Flows return [emptyFlow] by default since no JVM unit test so far
 * exercises observeByStatus. Tests that need it can override per-case.
 */
internal class FakeSessionDao : SessionDao {
    val rows: MutableMap<UUID7, SessionEntity> = linkedMapOf()

    fun put(row: SessionEntity) {
        rows[row.id] = row
    }

    override suspend fun upsert(session: SessionEntity) {
        rows[session.id] = session
    }

    override suspend fun update(session: SessionEntity): Int {
        if (session.id in rows) {
            rows[session.id] = session
            return 1
        }
        return 0
    }

    override suspend fun listByStatus(state: String): List<SessionEntity> = rows.values.filter { it.state == state }

    override fun observeByStatus(state: String): Flow<List<SessionEntity>> = emptyFlow()

    override suspend fun findById(id: UUID7): SessionEntity? = rows[id]

    override suspend fun deleteById(id: UUID7): Int = if (rows.remove(id) != null) 1 else 0
}
