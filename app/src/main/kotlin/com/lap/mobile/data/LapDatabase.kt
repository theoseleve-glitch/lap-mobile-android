package com.lap.mobile.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Root Room database. Schema version 1 ships with [SessionEntity]
 * (T042); subsequent entities (journal, upload queue, caches) land in
 * T043–T048 and bump the version via proper Migrations per T053 policy.
 *
 * [LapTypeConverters] is registered at the class level so every DAO
 * defined in subsequent tasks inherits Instant / UUID7 / SourceTag
 * converters without per-DAO `@TypeConverters` noise.
 */
@Database(
    entities = [
        SessionEntity::class,
        SessionJournalEntity::class,
        UploadQueueEntity::class,
        InsightCacheEntity::class,
        PrescriptionCacheEntity::class,
        TrackCacheEntity::class,
        RuleBundleEntity::class,
        LastUsedRaceboxEntity::class,
        UploadPreferencesEntity::class,
    ],
    version = 10,
    exportSchema = false,
)
@TypeConverters(LapTypeConverters::class)
abstract class LapDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    abstract fun journalDao(): JournalDao

    abstract fun uploadQueueDao(): UploadQueueDao

    abstract fun insightCacheDao(): InsightCacheDao

    abstract fun lastUsedRaceboxDao(): LastUsedRaceboxDao

    abstract fun uploadPreferencesDao(): UploadPreferencesDao

    companion object {
        const val DATABASE_NAME: String = "lap.db"
        const val SCHEMA_VERSION: Int = 10
    }
}
