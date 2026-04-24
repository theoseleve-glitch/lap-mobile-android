package com.lap.mobile.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lap.schemas.v1.UUID7
import java.time.Instant

/**
 * Offline cache of backend-authored [com.lap.schemas.v1.CoachingInsight]
 * rows, keyed by insight id with an FK to the session that produced
 * them. Payload is the kotlinx-serialized JSON (via
 * [com.lap.schemas.v1.LapSchemasJson]), not per-field columns — the
 * CoachingInsight shape is still in flux (T036 stub, Evidence and
 * MeasurableTarget pending Pydantic), and the UI only lists + renders
 * them, never filters on individual fields.
 *
 * Cascading FK so deleting a local session drops its cached insights.
 * [sessionId] is indexed for the common list-by-session dashboard query.
 */
@Entity(
    tableName = "insight_cache",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["session_id"])],
)
data class InsightCacheEntity(
    @PrimaryKey
    val id: UUID7,
    @ColumnInfo(name = "session_id")
    val sessionId: UUID7,
    @ColumnInfo(name = "driver_id")
    val driverId: UUID7,
    val payload: String,
    @ColumnInfo(name = "cached_at")
    val cachedAt: Instant,
)
