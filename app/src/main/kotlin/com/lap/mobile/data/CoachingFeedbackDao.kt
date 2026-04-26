package com.lap.mobile.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * DAO for the [CoachingFeedbackEntity] append-only feedback log
 * (T219).
 *
 * Three operations:
 *   - [insert] appends a new record (auto-id, no upsert — flagging
 *     the same rule twice should produce two records)
 *   - [loadAll] returns the full log in chronological order, used
 *     by a future feedback uploader to stream records to the server
 *   - [clear] empties the log after successful upload (and during
 *     test setup)
 *
 * No `getByRuleId` / `countByKind` because callers never need
 * partial slices — the full log is small (tens of records per
 * day at most for an active driver) and the upload-then-clear
 * lifecycle keeps it bounded.
 *
 * Tests can implement this interface directly with a
 * `MutableList<CoachingFeedbackEntity>` — same JVM-test pattern
 * as T213's RuleBundleDao FakeDao. Bypassing Room's KSP-generated
 * impl in JVM tests means no Robolectric, no Android dependency,
 * and the store logic gets exercised without the SQLite layer.
 */
@Dao
interface CoachingFeedbackDao {
    @Insert
    suspend fun insert(entity: CoachingFeedbackEntity)

    @Query("SELECT * FROM coaching_feedback ORDER BY recorded_at ASC")
    suspend fun loadAll(): List<CoachingFeedbackEntity>

    @Query("DELETE FROM coaching_feedback")
    suspend fun clear()
}
