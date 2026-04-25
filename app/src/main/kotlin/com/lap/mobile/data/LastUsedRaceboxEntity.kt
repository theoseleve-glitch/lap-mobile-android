package com.lap.mobile.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "last_used_racebox")
data class LastUsedRaceboxEntity(
    @PrimaryKey
    val id: String = SINGLETON_ID,
    val address: String,
    val name: String?,
    @ColumnInfo(name = "saved_at")
    val savedAt: Instant,
) {
    companion object {
        const val SINGLETON_ID: String = "singleton"
    }
}
