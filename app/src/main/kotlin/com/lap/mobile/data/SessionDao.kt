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
}
