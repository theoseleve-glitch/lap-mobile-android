package com.lap.mobile.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface LastUsedRaceboxDao {
    @Upsert
    suspend fun upsert(row: LastUsedRaceboxEntity)

    @Query("SELECT * FROM last_used_racebox WHERE id = '${LastUsedRaceboxEntity.SINGLETON_ID}' LIMIT 1")
    suspend fun get(): LastUsedRaceboxEntity?

    @Query("DELETE FROM last_used_racebox")
    suspend fun clear()
}
