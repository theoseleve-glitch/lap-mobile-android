package com.lap.mobile.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lap.schemas.v1.UUID7
import java.time.Instant

/**
 * Offline catalog of track metadata + sector splits. Rendered by the
 * dashboard trackmap (T195) and consumed by the preflight track-
 * selection check (T090) when the device is offline.
 *
 * [sectorsJson] is an opaque JSON blob — no Track Pydantic schema
 * exists yet, and once one does it may evolve independently; keeping
 * the payload as text isolates Room from that churn. [id] is the
 * global track UUIDv7 (backend catalog, shared across drivers), so
 * no FK to sessions — tracks outlive sessions.
 */
@Entity(tableName = "track_cache")
data class TrackCacheEntity(
    @PrimaryKey
    val id: UUID7,
    val name: String,
    val layout: String,
    @ColumnInfo(name = "sectors_json")
    val sectorsJson: String,
    @ColumnInfo(name = "cached_at")
    val cachedAt: Instant,
)
