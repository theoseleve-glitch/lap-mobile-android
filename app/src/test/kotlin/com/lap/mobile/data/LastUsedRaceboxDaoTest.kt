package com.lap.mobile.data

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.full.functions

/**
 * DAO-surface smoke test (T124). Locks method signatures; Room KSP
 * validates queries at compile time (annotations are non-reflective —
 * see saved memory). Real round-trip is instrumented + pending device.
 */
class LastUsedRaceboxDaoTest {
    @Test
    fun dao_declares_three_contract_methods() {
        val fnNames = LastUsedRaceboxDao::class.functions.map { it.name }.toSet()
        assertTrue("upsert missing: $fnNames", "upsert" in fnNames)
        assertTrue("get missing: $fnNames", "get" in fnNames)
        assertTrue("clear missing: $fnNames", "clear" in fnNames)
    }

    @Test
    fun get_is_nullable() {
        val fn = LastUsedRaceboxDao::class.functions.first { it.name == "get" }
        assertTrue(
            "get must return nullable LastUsedRaceboxEntity?, got ${fn.returnType}",
            fn.returnType.isMarkedNullable,
        )
    }

    @Test
    fun database_exposes_last_used_racebox_dao() {
        val fn = LapDatabase::class.functions.firstOrNull { it.name == "lastUsedRaceboxDao" }
        assertNotNull("LapDatabase must expose abstract fun lastUsedRaceboxDao(): LastUsedRaceboxDao", fn)
    }
}
