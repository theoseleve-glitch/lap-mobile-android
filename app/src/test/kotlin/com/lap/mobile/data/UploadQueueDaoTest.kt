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
class UploadQueueDaoTest {
    @Test
    fun dao_declares_three_contract_methods() {
        val fnNames = UploadQueueDao::class.functions.map { it.name }.toSet()
        assertTrue("enqueue missing: $fnNames", "enqueue" in fnNames)
        assertTrue("dequeueNext missing: $fnNames", "dequeueNext" in fnNames)
        assertTrue("updateAttempt missing: $fnNames", "updateAttempt" in fnNames)
    }

    @Test
    fun dequeue_next_returns_nullable_entity() {
        val fn = UploadQueueDao::class.functions.first { it.name == "dequeueNext" }
        assertTrue(
            "dequeueNext must return UploadQueueEntity?, got ${fn.returnType}",
            fn.returnType.classifier == UploadQueueEntity::class,
        )
        assertTrue(
            "dequeueNext must be nullable — queue can be empty",
            fn.returnType.isMarkedNullable,
        )
    }

    @Test
    fun update_attempt_returns_row_count() {
        val fn = UploadQueueDao::class.functions.first { it.name == "updateAttempt" }
        assertTrue(
            "updateAttempt must return Int (row count), got ${fn.returnType}",
            fn.returnType.classifier == Int::class,
        )
    }

    @Test
    fun database_exposes_upload_queue_dao() {
        val fn = LapDatabase::class.functions.firstOrNull { it.name == "uploadQueueDao" }
        assertNotNull("LapDatabase must expose abstract fun uploadQueueDao(): UploadQueueDao", fn)
    }
}
