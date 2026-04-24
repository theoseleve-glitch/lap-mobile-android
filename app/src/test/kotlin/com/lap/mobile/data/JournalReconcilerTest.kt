package com.lap.mobile.data

import com.lap.schemas.v1.UUID7
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private const val STALE_THRESHOLD_SECONDS: Long = 60L
private const val JUST_BEFORE_BOUNDARY_SECONDS: Long = 59L
private const val PAST_BOUNDARY_SECONDS: Long = 120L
private const val RECENT_SECONDS: Long = 10L

/**
 * Virtual-time tests for the journal startup reconciler. Threshold is
 * strict (`>` not `>=`), so a row exactly at the boundary is NOT
 * treated as stale — avoids flapping on devices where the last
 * heartbeat landed milliseconds before app kill.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JournalReconcilerTest {
    private val now: Instant = Instant.parse("2026-04-24T09:00:00Z")
    private val clock: Clock = FixedClock(now)

    @Test
    fun empty_journal_reports_no_stale_rows() = runTest {
        val reconciler = JournalReconciler(FakeJournalDao(), clock)
        assertEquals(emptyList<SessionJournalEntity>(), reconciler.findStaleSessions())
    }

    @Test
    fun recent_heartbeat_is_not_stale() = runTest {
        val dao = FakeJournalDao().apply {
            put(journalRow("s-recent", now.minusSeconds(RECENT_SECONDS)))
        }
        val reconciler = JournalReconciler(dao, clock)
        assertEquals(
            "10 s old < 60 s threshold — not stale",
            emptyList<SessionJournalEntity>(),
            reconciler.findStaleSessions(),
        )
    }

    @Test
    fun heartbeat_older_than_threshold_is_stale() = runTest {
        val stale = journalRow("s-stale", now.minusSeconds(PAST_BOUNDARY_SECONDS))
        val dao = FakeJournalDao().apply { put(stale) }
        val reconciler = JournalReconciler(dao, clock)
        assertEquals(listOf(stale), reconciler.findStaleSessions())
    }

    @Test
    fun heartbeat_at_exact_boundary_is_not_stale() = runTest {
        val onBoundary = journalRow(
            "s-boundary",
            now.minusSeconds(STALE_THRESHOLD_SECONDS),
        )
        val dao = FakeJournalDao().apply { put(onBoundary) }
        val reconciler = JournalReconciler(dao, clock)
        assertEquals(
            "`>` not `>=` — exactly 60 s old is still fresh",
            emptyList<SessionJournalEntity>(),
            reconciler.findStaleSessions(),
        )
    }

    @Test
    fun heartbeat_one_second_before_boundary_is_not_stale() = runTest {
        val dao = FakeJournalDao().apply {
            put(journalRow("s-just-fresh", now.minusSeconds(JUST_BEFORE_BOUNDARY_SECONDS)))
        }
        val reconciler = JournalReconciler(dao, clock)
        assertEquals(emptyList<SessionJournalEntity>(), reconciler.findStaleSessions())
    }

    @Test
    fun mixed_rows_return_only_stale() = runTest {
        val staleA = journalRow("s-a", now.minusSeconds(PAST_BOUNDARY_SECONDS))
        val staleB = journalRow(
            "s-b",
            now.minusSeconds(STALE_THRESHOLD_SECONDS + 1L),
        )
        val dao = FakeJournalDao().apply {
            put(staleA)
            put(staleB)
            put(journalRow("s-fresh", now.minusSeconds(RECENT_SECONDS)))
        }
        val reconciler = JournalReconciler(dao, clock)
        val stale = reconciler.findStaleSessions().toSet()
        assertEquals(setOf(staleA, staleB), stale)
        assertTrue(stale.none { it.sessionId == "s-fresh" })
    }

    private fun journalRow(sessionId: UUID7, lastHeartbeatAt: Instant): SessionJournalEntity = SessionJournalEntity(
        sessionId = sessionId,
        lastHeartbeatAt = lastHeartbeatAt,
        status = "recording",
    )
}

private class FixedClock(private val now: Instant) : Clock() {
    override fun instant(): Instant = now
    override fun getZone(): java.time.ZoneId = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId?): Clock = this
}
