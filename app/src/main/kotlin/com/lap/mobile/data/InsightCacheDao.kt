package com.lap.mobile.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lap.schemas.v1.UUID7

/**
 * DAO for the session-level insight cache. The sync path writes a
 * batch of CoachingInsights per session (a single HTTP roundtrip
 * returns many rows), so [upsertBatch] takes a list rather than one
 * row at a time. [listForSession] feeds the dashboard's offline
 * insight list (T195 onward).
 */
@Dao
interface InsightCacheDao {
    @Upsert
    suspend fun upsertBatch(rows: List<InsightCacheEntity>)

    @Query(
        """
        SELECT * FROM insight_cache
        WHERE session_id = :sessionId
        ORDER BY cached_at DESC
        """,
    )
    suspend fun listForSession(sessionId: UUID7): List<InsightCacheEntity>
}
