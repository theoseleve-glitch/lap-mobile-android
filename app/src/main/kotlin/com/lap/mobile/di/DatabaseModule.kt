package com.lap.mobile.di

import android.content.Context
import androidx.room.Room
import com.lap.mobile.BuildConfig
import com.lap.mobile.data.LapDatabase
import com.lap.mobile.data.UploadQueueDao
import com.lap.mobile.data.migrationPolicyFor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val FLAVOR_DEV: String = "dev"

/**
 * Provides [LapDatabase] + DAOs to the Hilt graph (T146 + T147 needed
 * UploadQueueDao first; future tasks add more DAO accessors here as
 * they need them).
 *
 * Migration policy follows T053: dev flavor allows destructive
 * fallback to unblock rapid schema iteration; staging / prod use
 * strict migrations only — re-validated at every Room open call so
 * a missing migration throws at startup rather than silently wiping
 * user data.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideLapDatabase(@ApplicationContext context: Context): LapDatabase {
        val builder = Room.databaseBuilder(context, LapDatabase::class.java, LapDatabase.DATABASE_NAME)
        migrationPolicyFor(destructiveMigrationAllowed = BuildConfig.FLAVOR == FLAVOR_DEV).applyTo(builder)
        return builder.build()
    }

    @Provides
    fun provideUploadQueueDao(db: LapDatabase): UploadQueueDao = db.uploadQueueDao()
}
