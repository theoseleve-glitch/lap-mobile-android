package com.lap.mobile.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/**
 * DAO for the singleton [UploadPreferencesEntity] row (T156). Used by
 * [UploadPreferencesRepository] to read the user's upload toggles
 * (network constraint today; battery + WiFi-only follow in T157/T158).
 *
 * `get(id)` defaults to the [UploadPreferencesEntity.SINGLETON_ID]
 * sentinel so callers can write `dao.get()` without the literal at
 * every call site.
 */
@Dao
interface UploadPreferencesDao {
    @Query("SELECT * FROM upload_preferences WHERE id = :id LIMIT 1")
    suspend fun get(id: String = UploadPreferencesEntity.SINGLETON_ID): UploadPreferencesEntity?

    @Upsert
    suspend fun upsert(row: UploadPreferencesEntity)
}
