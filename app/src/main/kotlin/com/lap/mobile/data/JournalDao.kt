package com.lap.mobile.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lap.schemas.v1.UUID7
import java.time.Instant

/**
 * DAO for the session journal. Heartbeat writer (T054) calls
 * [upsertHeartbeat] every 5 s during recording; the startup
 * reconciler (T055) calls [listStale] at app launch to find sessions
 * whose last heartbeat is older than the configured threshold
 * (ADR-012 recovery path — a stale row means the app crashed mid-
 * recording and the user can Finalize-from-journal via T059).
 *
 * Upsert is the right verb here: the first heartbeat per session
 * inserts the row; every subsequent heartbeat from the same session
 * updates [SessionJournalEntity.lastHeartbeatAt] +
 * [SessionJournalEntity.fileOffset].
 */
@Dao
interface JournalDao {
    @Upsert
    suspend fun upsertHeartbeat(entry: SessionJournalEntity)

    @Query("SELECT * FROM session_journal WHERE last_heartbeat_at < :before")
    suspend fun listStale(before: Instant): List<SessionJournalEntity>

    @Query("SELECT * FROM session_journal WHERE session_id = :sessionId")
    suspend fun findBySessionId(sessionId: UUID7): SessionJournalEntity?

    @Query("DELETE FROM session_journal WHERE last_heartbeat_at < :before")
    suspend fun deleteOlderThan(before: Instant): Int
}
