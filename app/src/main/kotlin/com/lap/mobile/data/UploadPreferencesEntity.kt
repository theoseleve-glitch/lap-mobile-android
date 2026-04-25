package com.lap.mobile.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Singleton-row table holding the user's upload preferences (T156+).
 *
 * Pattern mirrors [LastUsedRaceboxEntity]: at most one row per app
 * install, identified by the literal [SINGLETON_ID] sentinel. Toggles
 * extend this row rather than minting another table — keeps the
 * worker's pre-flight read down to a single SELECT.
 *
 * Toggles:
 *  - [allowMetered] (T156): `false` (default) → worker only runs on
 *    UNMETERED networks (Wi-Fi); `true` → also use cellular.
 *  - [forceWhenBatteryLow] (T158): `false` (default) → worker defers
 *    while battery is low; `true` → user opts to push uploads through
 *    even on a near-empty battery (e.g., plugged into a tender at the
 *    track and wants the session uploaded before disconnecting).
 *
 * Default value on [forceWhenBatteryLow] keeps existing call sites
 * (T156 setAllowMetered path) working without touching every literal —
 * Room is happy because the column has a SQL DEFAULT in the migration.
 */
@Entity(tableName = "upload_preferences")
data class UploadPreferencesEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "allow_metered")
    val allowMetered: Boolean,
    @ColumnInfo(name = "force_when_battery_low")
    val forceWhenBatteryLow: Boolean = false,
) {
    companion object {
        const val SINGLETON_ID: String = "singleton"
    }
}
