package com.lap.mobile.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Home for manual [Migration] objects wired into Room at builder time
 * per T053 strategy (strict migrations in prod, destructive fallback in
 * dev). Each migration object lives here so the full schema history is
 * searchable in one file; DAOs stay out of the migration authoring
 * business.
 */

/**
 * v1 → v2: add `session_journal` (T043). The table carries a cascading
 * FK to `sessions.id` so dropping a session drops its journal row. CREATE
 * mirrors the shape of [SessionJournalEntity]; Room's instrumented
 * migration test (T053+) diffs this against the KSP-generated schema
 * JSON to verify column-for-column fidelity.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS session_journal (
                session_id TEXT NOT NULL PRIMARY KEY,
                last_heartbeat_at TEXT NOT NULL,
                status TEXT NOT NULL,
                file_offset INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(session_id) REFERENCES sessions(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
    }
}
