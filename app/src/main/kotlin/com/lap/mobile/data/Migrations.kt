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

/**
 * v2 → v3: add `upload_queue` (T044). Cascading FK on session_id so
 * abandoning a local session (T162) sweeps its queue row too. Indexed
 * on state so T160 FIFO dequeue stays cheap as the queue grows.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS upload_queue (
                session_id TEXT NOT NULL PRIMARY KEY,
                state TEXT NOT NULL,
                chunks_total INTEGER NOT NULL,
                idempotency_key TEXT NOT NULL,
                attempt_count INTEGER NOT NULL DEFAULT 0,
                next_retry_at TEXT,
                last_http_code INTEGER,
                last_error TEXT,
                chunks_sent INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(session_id) REFERENCES sessions(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_upload_queue_state ON upload_queue(state)")
    }
}

/**
 * v3 → v4: add `insight_cache` (T045). Payload is kotlinx-serialized
 * JSON of a [com.lap.schemas.v1.CoachingInsight], keyed by insight id
 * with a session_id FK for cascade cleanup and an index for the
 * list-by-session dashboard query.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS insight_cache (
                id TEXT NOT NULL PRIMARY KEY,
                session_id TEXT NOT NULL,
                driver_id TEXT NOT NULL,
                payload TEXT NOT NULL,
                cached_at TEXT NOT NULL,
                FOREIGN KEY(session_id) REFERENCES sessions(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_insight_cache_session_id ON insight_cache(session_id)")
    }
}

/**
 * v4 → v5: add `prescription_cache` (T046). No FK to sessions —
 * prescriptions are longitudinal and may outlive individual sessions
 * (ADR-012, backend-authored per driver). Indexed on driver_id for
 * the list-by-driver dashboard query.
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS prescription_cache (
                id TEXT NOT NULL PRIMARY KEY,
                driver_id TEXT NOT NULL,
                payload TEXT NOT NULL,
                cached_at TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_prescription_cache_driver_id ON prescription_cache(driver_id)")
    }
}

/**
 * v5 → v6: add `track_cache` (T047). Global catalog — no FK, no index
 * beyond the implicit PK index (track lookups are by id).
 */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS track_cache (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                layout TEXT NOT NULL,
                sectors_json TEXT NOT NULL,
                cached_at TEXT NOT NULL
            )
            """.trimIndent(),
        )
    }
}
