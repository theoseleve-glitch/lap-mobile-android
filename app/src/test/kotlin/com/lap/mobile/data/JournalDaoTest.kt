package com.lap.mobile.data

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.full.functions

/**
 * DAO-surface smoke test: locks method signatures the rest of the app
 * will call so refactors fail fast. Room KSP validates the queries at
 * compile time (annotations use RetentionPolicy.CLASS and are not
 * reflective at runtime — see memory). Real round-trip tests are
 * instrumented (T083+).
 */
class JournalDaoTest {
    @Test
    fun dao_declares_two_contract_methods() {
        val fnNames = JournalDao::class.functions.map { it.name }.toSet()
        assertTrue("upsertHeartbeat missing: $fnNames", "upsertHeartbeat" in fnNames)
        assertTrue("listStale missing: $fnNames", "listStale" in fnNames)
    }

    @Test
    fun list_stale_returns_list_of_journal_rows() {
        val fn = JournalDao::class.functions.first { it.name == "listStale" }
        assertTrue(
            "listStale must return List<SessionJournalEntity>, got ${fn.returnType}",
            fn.returnType.classifier == List::class,
        )
    }

    @Test
    fun database_exposes_journal_dao() {
        val fn = LapDatabase::class.functions.firstOrNull { it.name == "journalDao" }
        assertNotNull("LapDatabase must expose abstract fun journalDao(): JournalDao", fn)
    }
}
