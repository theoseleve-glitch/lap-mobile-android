package com.lap.mobile.data

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.full.functions

/**
 * DAO-surface smoke test: locks method signatures; Room KSP validates
 * queries at compile time (annotations non-reflective — see memory).
 * Real round-trip is instrumented (T083+).
 */
class InsightCacheDaoTest {
    @Test
    fun dao_declares_two_contract_methods() {
        val fnNames = InsightCacheDao::class.functions.map { it.name }.toSet()
        assertTrue("upsertBatch missing: $fnNames", "upsertBatch" in fnNames)
        assertTrue("listForSession missing: $fnNames", "listForSession" in fnNames)
    }

    @Test
    fun upsert_batch_accepts_a_list_of_rows() {
        val fn = InsightCacheDao::class.functions.first { it.name == "upsertBatch" }
        val firstParamType = fn.parameters.firstOrNull { it.kind.name == "VALUE" }?.type
        assertNotNull("upsertBatch must accept a value parameter", firstParamType)
        assertTrue(
            "upsertBatch must take List<InsightCacheEntity>, got $firstParamType",
            firstParamType!!.classifier == List::class,
        )
    }

    @Test
    fun list_for_session_returns_list() {
        val fn = InsightCacheDao::class.functions.first { it.name == "listForSession" }
        assertTrue(
            "listForSession must return List, got ${fn.returnType}",
            fn.returnType.classifier == List::class,
        )
    }

    @Test
    fun database_exposes_insight_cache_dao() {
        val fn = LapDatabase::class.functions.firstOrNull { it.name == "insightCacheDao" }
        assertNotNull("LapDatabase must expose abstract fun insightCacheDao(): InsightCacheDao", fn)
    }
}
