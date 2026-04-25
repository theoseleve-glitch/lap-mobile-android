package com.lap.mobile.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Singleton-row table holding the user's upload preferences (T156+).
 *
 * Pattern mirrors [LastUsedRaceboxEntity]: at most one row per app
 * install, identified by the literal [SINGLETON_ID] sentinel. Future
 * preference toggles (T157 WiFi-only, T158 battery override) extend
 * this row rather than minting another table — keeps the worker's
 * pre-flight read down to a single SELECT.
 *
 * [allowMetered] is the T156 toggle: `false` (default) means the upload
 * worker only runs on UNMETERED networks (typically Wi-Fi); `true`
 * lets it also run over a metered cellular connection.
 */
@Entity(tableName = "upload_preferences")
data class UploadPreferencesEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "allow_metered")
    val allowMetered: Boolean,
) {
    companion object {
        const val SINGLETON_ID: String = "singleton"
    }
}
