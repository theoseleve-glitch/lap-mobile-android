package com.lap.mobile.data

import kotlinx.coroutines.flow.Flow
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.full.functions

/**
 * DAO-surface smoke test. Real Room round-trips require an Android
 * runtime (instrumented tests from T083 onward); this JVM test only
 * locks the method signatures the rest of the app will call, so
 * refactors that rename or re-shape the DAO surface fail fast.
 *
 * Room annotations (`@Dao`, `@Query`, `@Upsert`, `@Update`) use
 * `RetentionPolicy.CLASS` — present in the `.class` file for KSP but
 * stripped before runtime reflection, so this test cannot assert
 * their presence directly. Room KSP is the annotation-correctness
 * gate: `./gradlew app:assembleDebug` fails if an annotation is
 * missing or a query is malformed.
 */
class SessionDaoTest {
    @Test
    fun dao_declares_four_contract_methods() {
        val fnNames = SessionDao::class.functions.map { it.name }.toSet()
        assertTrue("upsert missing: $fnNames", "upsert" in fnNames)
        assertTrue("update missing: $fnNames", "update" in fnNames)
        assertTrue("listByStatus missing: $fnNames", "listByStatus" in fnNames)
        assertTrue("observeByStatus missing: $fnNames", "observeByStatus" in fnNames)
    }

    @Test
    fun observe_by_status_returns_flow() {
        val fn = SessionDao::class.functions.first { it.name == "observeByStatus" }
        val returnClassifier = fn.returnType.classifier
        assertTrue(
            "observeByStatus must return Flow, got $returnClassifier",
            returnClassifier == Flow::class,
        )
    }

    @Test
    fun list_by_status_returns_list() {
        val fn = SessionDao::class.functions.first { it.name == "listByStatus" }
        val returnClassifier = fn.returnType.classifier
        assertTrue(
            "listByStatus must return List, got $returnClassifier",
            returnClassifier == List::class,
        )
    }

    @Test
    fun database_exposes_session_dao() {
        val fn = LapDatabase::class.functions.firstOrNull { it.name == "sessionDao" }
        assertNotNull("LapDatabase must expose abstract fun sessionDao(): SessionDao", fn)
    }
}
