package com.lap.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T043 requires a Migration object for the v1 → v2 bump that adds
 * session_journal. Actual SQL execution + schema validation happens in
 * instrumented migration tests (T053+). Here we only lock the version
 * endpoints so the migration never gets wired into the wrong slot.
 */
class MigrationsTest {
    @Test
    fun migration_1_2_declares_correct_version_range() {
        assertEquals(1, MIGRATION_1_2.startVersion)
        assertEquals(2, MIGRATION_1_2.endVersion)
    }
}
