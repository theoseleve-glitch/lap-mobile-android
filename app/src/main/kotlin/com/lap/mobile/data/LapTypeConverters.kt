package com.lap.mobile.data

import androidx.room.TypeConverter
import com.lap.schemas.v1.UUID7
import java.time.Instant

/**
 * Room TypeConverters for types that DAOs pass around natively but
 * SQLite doesn't speak. Registered on [LapDatabase] via
 * `@TypeConverters(LapTypeConverters::class)` so every DAO inherits
 * them.
 *
 * - [Instant] stores as RFC3339 (Instant.toString()), matching the
 *   wire format [com.lap.schemas.v1.LapSchemasJson] uses for timestamps;
 *   this keeps the journal DB copy byte-identical to what the backend
 *   eventually ingests (ADR-012) and human-readable in SQLite browsers.
 * - [UUID7] is a String typealias today; the converter is identity so
 *   a future promotion to a value class doesn't ripple across every DAO.
 */
class LapTypeConverters {
    @TypeConverter
    fun fromInstant(value: Instant?): String? = value?.toString()

    @TypeConverter
    fun toInstant(value: String?): Instant? = value?.let(Instant::parse)

    @TypeConverter
    fun fromUuid7(value: UUID7?): String? = value

    @TypeConverter
    fun toUuid7(value: String?): UUID7? = value
}
