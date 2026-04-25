package com.lap.mobile.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.lap.schemas.v1.UUID7
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the journal's session rows. Session lifecycle walks through
 * the recording state machine (T075): IDLE → ARMED → RECORDING →
 * FINALIZING → COMPLETED / FAILED, with [upsert] used both for the
 * initial insert at ARMED and for state transitions thereafter.
 *
 * [listByStatus] is a point-in-time snapshot used by startup
 * reconciliation (T055) and unit/integration tests; [observeByStatus]
 * feeds reactive UI (SessionList, Uploads screen).
 */
@Dao
interface SessionDao {
    @Upsert
    suspend fun upsert(session: SessionEntity)

    @Update
    suspend fun update(session: SessionEntity): Int

    @Query("SELECT * FROM sessions WHERE state = :state ORDER BY started_at DESC")
    suspend fun listByStatus(state: String): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE state = :state ORDER BY started_at DESC")
    fun observeByStatus(state: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun findById(id: UUID7): SessionEntity?

    /**
     * Hard-deletes the session row. The `upload_queue` row that
     * references this session via `ON DELETE CASCADE` is swept by
     * SQLite as part of the same transaction (see [UploadQueueEntity]).
     * Returns the number of rows deleted (0 or 1) so callers can
     * distinguish a real removal from a no-op when the row was already
     * gone (concurrent T162 abandon, server-driven completion sweep,
     * etc).
     */
    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: UUID7): Int
}
