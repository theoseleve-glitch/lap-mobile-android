package com.lap.mobile.data

import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Root Room database. T041 lands the skeleton + converter registration;
 * the `@Database(entities = [...], version = ...)` annotation arrives in
 * T042 when the first real entity ([SessionEntity]) is defined — Room
 * refuses to process an empty entities array.
 *
 * [LapTypeConverters] is registered at the class level so every DAO
 * defined in subsequent tasks inherits Instant / UUID7 converters
 * without per-DAO `@TypeConverters` noise.
 */
@TypeConverters(LapTypeConverters::class)
abstract class LapDatabase : RoomDatabase() {
    companion object {
        const val DATABASE_NAME: String = "lap.db"
        const val SCHEMA_VERSION: Int = 1
    }
}
