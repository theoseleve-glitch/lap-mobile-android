package com.lap.mobile.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lap.schemas.v1.UUID7
import java.time.Instant

/**
 * Offline cache of backend-authored [com.lap.schemas.v1.ActionPrescription]
 * rows. Keyed by prescription id; indexed by [driverId] for the common
 * list-by-driver dashboard query. Payload is kotlinx-serialized JSON
 * via [com.lap.schemas.v1.LapSchemasJson] so T035 shape churn stays
 * out of Room migrations.
 *
 * Unlike [InsightCacheEntity] there is NO cascading FK to a session:
 * prescriptions are longitudinal — they may span multiple sessions,
 * reference sessions that haven't been captured yet, or have no
 * session attached at all (ADR-012: backend-authored, per-driver). A
 * session delete should NOT sweep prescriptions.
 */
@Entity(
    tableName = "prescription_cache",
    indices = [Index(value = ["driver_id"])],
)
data class PrescriptionCacheEntity(
    @PrimaryKey
    val id: UUID7,
    @ColumnInfo(name = "driver_id")
    val driverId: UUID7,
    val payload: String,
    @ColumnInfo(name = "cached_at")
    val cachedAt: Instant,
)
