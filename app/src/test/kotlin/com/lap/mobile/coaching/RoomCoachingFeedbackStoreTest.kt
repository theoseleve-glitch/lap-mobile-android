package com.lap.mobile.coaching

import com.lap.mobile.data.CoachingFeedbackDao
import com.lap.mobile.data.CoachingFeedbackEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Locks the contract for [RoomCoachingFeedbackStore] (T219) — the
 * Room-backed implementation of the [CoachingFeedbackStore]
 * interface persisting user dismissal + flag-as-wrong events.
 *
 * Coverage:
 *   - recordDismissal stores entity with DISMISSED kind
 *   - recordFlaggedWrong stores entity with FLAGGED_WRONG kind
 *   - Multiple records for same rule all persist (append-only,
 *     no dedup — server analytics needs counts not just presence)
 *   - loadAll returns records in chronological order so feedback
 *     uploader sees the timeline as it happened
 *   - clear empties the store (called after successful upload)
 *   - Clock injection: recordedAt comes from injected Clock so
 *     tests are deterministic without Thread.sleep
 *   - Forward-compat: unknown kind String from DB → DISMISSED
 *     default rather than dropping the record
 *
 * Why a fake [CoachingFeedbackDao] instead of in-memory Room: same
 * rationale as T213 RoomRuleBundleCacheTest — Room's @Dao is a
 * Kotlin interface, KSP generates the production impl at compile
 * time, JVM tests can implement the interface directly with a
 * MutableList<CoachingFeedbackEntity>. Room-to-SQLite mapping +
 * the v10→v11 migration is verified by instrumented tests that
 * ship later. What we test here: the store LOGIC (kind round-trip,
 * ordering, append semantics, forward-compat fallback).
 */
class RoomCoachingFeedbackStoreTest {
    @Test
    fun record_dismissal_stores_dismissed_kind_so_user_acknowledged_signal_is_persisted() = runTest {
        val dao = FakeCoachingFeedbackDao()
        val store = RoomCoachingFeedbackStore(dao = dao, clock = fixedClockAt(RECORD_TIME))

        store.recordDismissal(ruleId = "rule-late-braking")

        val records = store.loadAll()
        assertEquals(1, records.size)
        assertEquals("rule-late-braking", records[0].ruleId)
        assertEquals(CoachingFeedbackKind.DISMISSED, records[0].kind)
        assertEquals(RECORD_TIME, records[0].recordedAt)
    }

    @Test
    fun record_flagged_wrong_stores_flagged_wrong_kind_so_false_positive_signal_is_persisted() = runTest {
        // Headline T219 invariant: flag-as-wrong is the strong
        // "this rule shouldn't have fired" signal that drives
        // server-side rule re-tuning. Distinct from DISMISSED
        // which is just "user saw this".
        val dao = FakeCoachingFeedbackDao()
        val store = RoomCoachingFeedbackStore(dao = dao, clock = fixedClockAt(RECORD_TIME))

        store.recordFlaggedWrong(ruleId = "rule-late-braking")

        val records = store.loadAll()
        assertEquals(1, records.size)
        assertEquals(CoachingFeedbackKind.FLAGGED_WRONG, records[0].kind)
    }

    @Test
    fun multiple_records_for_same_rule_all_persist_so_server_analytics_can_count_repeated_flags() = runTest {
        // Append-only by design — each user action records a new
        // entry even if the same rule was flagged before. Server's
        // rule-tuner counts flags per rule per session.
        val dao = FakeCoachingFeedbackDao()
        val store = RoomCoachingFeedbackStore(dao = dao, clock = fixedClockAt(RECORD_TIME))

        store.recordFlaggedWrong(ruleId = "rule-X")
        store.recordFlaggedWrong(ruleId = "rule-X")
        store.recordFlaggedWrong(ruleId = "rule-X")

        assertEquals(3, store.loadAll().size)
    }

    @Test
    fun load_all_returns_records_in_chronological_order_so_uploader_sees_timeline_as_it_happened() = runTest {
        val dao = FakeCoachingFeedbackDao()
        val firstStore = RoomCoachingFeedbackStore(dao = dao, clock = fixedClockAt(RECORD_TIME))
        firstStore.recordDismissal(ruleId = "rule-first")

        val secondStore = RoomCoachingFeedbackStore(
            dao = dao,
            clock = fixedClockAt(RECORD_TIME.plusSeconds(60)),
        )
        secondStore.recordFlaggedWrong(ruleId = "rule-second")

        val thirdStore = RoomCoachingFeedbackStore(
            dao = dao,
            clock = fixedClockAt(RECORD_TIME.plusSeconds(120)),
        )
        thirdStore.recordDismissal(ruleId = "rule-third")

        val records = thirdStore.loadAll()
        assertEquals(
            listOf("rule-first", "rule-second", "rule-third"),
            records.map { it.ruleId },
        )
    }

    @Test
    fun clear_empties_the_store_so_post_upload_lifecycle_keeps_table_bounded() = runTest {
        val dao = FakeCoachingFeedbackDao()
        val store = RoomCoachingFeedbackStore(dao = dao, clock = fixedClockAt(RECORD_TIME))
        store.recordDismissal(ruleId = "rule-X")
        store.recordFlaggedWrong(ruleId = "rule-Y")

        store.clear()

        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun load_all_on_empty_store_returns_empty_list_so_first_run_doesnt_crash() = runTest {
        val store = RoomCoachingFeedbackStore(dao = FakeCoachingFeedbackDao(), clock = fixedClockAt(RECORD_TIME))

        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun unknown_kind_string_falls_back_to_dismissed_so_forward_compat_with_newer_clients_is_preserved() = runTest {
        // Forward-compat: a newer client may write a kind value the
        // older client doesn't know. valueOf would throw; we fall
        // back to DISMISSED rather than dropping the record so
        // rule_id + timestamp signal is preserved during partial-
        // rollout downgrade scenarios.
        val dao = FakeCoachingFeedbackDao().apply {
            // Bypass the store layer to insert a kind string that
            // CoachingFeedbackKind doesn't know about.
            insertRaw(
                CoachingFeedbackEntity(
                    ruleId = "rule-future",
                    kind = "ESCALATED_TO_SUPPORT",
                    recordedAt = RECORD_TIME,
                ),
            )
        }
        val store = RoomCoachingFeedbackStore(dao = dao, clock = fixedClockAt(RECORD_TIME))

        val records = store.loadAll()

        assertEquals(1, records.size)
        assertEquals(CoachingFeedbackKind.DISMISSED, records[0].kind)
        assertEquals("rule-future", records[0].ruleId)
    }

    @Test
    fun clock_injection_drives_recorded_at_so_tests_are_deterministic_without_sleep() = runTest {
        val dao = FakeCoachingFeedbackDao()
        val expectedTime = Instant.parse("2026-04-26T15:30:45.123Z")
        val store = RoomCoachingFeedbackStore(dao = dao, clock = fixedClockAt(expectedTime))

        store.recordDismissal(ruleId = "rule-time-test")

        assertEquals(expectedTime, store.loadAll().single().recordedAt)
    }

    private fun fixedClockAt(instant: Instant): Clock = Clock.fixed(instant, ZoneOffset.UTC)

    private companion object {
        val RECORD_TIME: Instant = Instant.parse("2026-04-26T12:00:00Z")
    }
}

/**
 * In-memory fake of [CoachingFeedbackDao] for JVM tests. Mirrors
 * Room's auto-incrementing PK + chronological-load semantics with
 * plain Kotlin so the store logic can be tested without Robolectric.
 *
 * `insertRaw` is a test-only escape hatch that lets a test seed the
 * "DAO" with rows whose `kind` doesn't round-trip through the store
 * — used by the forward-compat test to inject an unknown kind
 * string without going through the store's enum-typed API.
 */
private class FakeCoachingFeedbackDao : CoachingFeedbackDao {
    private val rows: MutableList<CoachingFeedbackEntity> = mutableListOf()
    private var nextId: Long = 1L

    override suspend fun insert(entity: CoachingFeedbackEntity) {
        rows += entity.copy(id = nextId++)
    }

    override suspend fun loadAll(): List<CoachingFeedbackEntity> = rows.sortedBy { it.recordedAt }

    override suspend fun clear() {
        rows.clear()
    }

    fun insertRaw(entity: CoachingFeedbackEntity) {
        rows += entity.copy(id = nextId++)
    }
}
