package com.lap.mobile.data

import androidx.room.TypeConverter
import com.lap.schemas.v1.SourceTag
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
 * - [SourceTag] stores as its wireValue string, so ingest-rejected
 *   values can't even be written to the local DB in the first place.
 *
 * UUID7 has no converter: it's a String typealias, so Room persists it
 * natively — adding an identity converter trips KSP with "multiple
 * methods define the same conversion".
 */
class LapTypeConverters {
    @TypeConverter
    fun fromInstant(value: Instant?): String? = value?.toString()

    @TypeConverter
    fun toInstant(value: String?): Instant? = value?.let(Instant::parse)

    @TypeConverter
    fun fromSourceTag(value: SourceTag?): String? = value?.wireValue

    @TypeConverter
    fun toSourceTag(value: String?): SourceTag? = value?.let(SourceTag::fromWire)
}
