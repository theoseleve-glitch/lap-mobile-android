package com.lap.mobile.data

import com.lap.schemas.v1.UUID7
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant

private const val RETENTION_DAYS: Long = 7L
private const val RECENT_DAYS: Long = 1L
private const val PAST_BOUNDARY_DAYS: Long = 8L
private const val ONE_SECOND: Long = 1L

/**
 * Virtual-time tests for [JournalRetention]. The retention threshold is
 * strict (`<`, not `<=`), matching [JournalReconciler]'s convention —
 * a row exactly at the 7-day boundary is preserved. Deletion is gated
 * by `confirmedByUser=true` so an automated startup sweep can't quietly
 * destroy recoverable journal rows without the user's say-so.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JournalRetentionTest {
    private val now: Instant = Instant.parse("2026-04-24T09:00:00Z")
    private val clock: Clock = FixedClock(now)

    @Test
    fun empty_journal_returns_zero_even_when_confirmed() = runTest {
        val retention = JournalRetention(FakeJournalDao(), clock)
        assertEquals(0, retention.cleanup(confirmedByUser = true))
    }

    @Test
    fun rows_within_retention_are_preserved_when_confirmed() = runTest {
        val fresh = journalRow("s-fresh", now.minus(Duration.ofDays(RECENT_DAYS)))
        val dao = FakeJournalDao().apply { put(fresh) }
        val retention = JournalRetention(dao, clock)

        val deleted = retention.cleanup(confirmedByUser = true)

        assertEquals("1-day-old row is well within 7-day retention", 0, deleted)
        assertEquals(fresh, dao.rows[fresh.sessionId])
    }

    @Test
    fun rows_older_than_retention_are_deleted_when_confirmed() = runTest {
        val stale = journalRow("s-stale", now.minus(Duration.ofDays(PAST_BOUNDARY_DAYS)))
        val dao = FakeJournalDao().apply { put(stale) }
        val retention = JournalRetention(dao, clock)

        val deleted = retention.cleanup(confirmedByUser = true)

        assertEquals(1, deleted)
        assertTrue("8-day-old row removed", dao.rows.isEmpty())
    }

    @Test
    fun rows_older_than_retention_are_preserved_when_not_confirmed() = runTest {
        val stale = journalRow("s-stale", now.minus(Duration.ofDays(PAST_BOUNDARY_DAYS)))
        val dao = FakeJournalDao().apply { put(stale) }
        val retention = JournalRetention(dao, clock)

        val deleted = retention.cleanup(confirmedByUser = false)

        assertEquals("user-not-confirmed must short-circuit", 0, deleted)
        assertEquals(
            "row preserved without confirmation — recovery still possible",
            stale,
            dao.rows[stale.sessionId],
        )
    }

    @Test
    fun row_at_exact_retention_boundary_is_preserved() = runTest {
        val onBoundary = journalRow(
            "s-boundary",
            now.minus(Duration.ofDays(RETENTION_DAYS)),
        )
        val dao = FakeJournalDao().apply { put(onBoundary) }
        val retention = JournalRetention(dao, clock)

        val deleted = retention.cleanup(confirmedByUser = true)

        assertEquals(
            "`<` not `<=` — exactly 7 days old is still within retention",
            0,
            deleted,
        )
        assertEquals(onBoundary, dao.rows[onBoundary.sessionId])
    }

    @Test
    fun mixed_ages_only_strictly_older_rows_deleted() = runTest {
        val fresh = journalRow("s-fresh", now.minus(Duration.ofDays(RECENT_DAYS)))
        val onBoundary = journalRow(
            "s-boundary",
            now.minus(Duration.ofDays(RETENTION_DAYS)),
        )
        val justStale = journalRow(
            "s-just-stale",
            now.minus(Duration.ofDays(RETENTION_DAYS)).minusSeconds(ONE_SECOND),
        )
        val veryStale = journalRow("s-very-stale", now.minus(Duration.ofDays(PAST_BOUNDARY_DAYS)))
        val dao = FakeJournalDao().apply {
            put(fresh)
            put(onBoundary)
            put(justStale)
            put(veryStale)
        }
        val retention = JournalRetention(dao, clock)

        val deleted = retention.cleanup(confirmedByUser = true)

        assertEquals(2, deleted)
        assertEquals(
            setOf(fresh.sessionId, onBoundary.sessionId),
            dao.rows.keys,
        )
    }

    private fun journalRow(sessionId: UUID7, lastHeartbeatAt: Instant): SessionJournalEntity = SessionJournalEntity(
        sessionId = sessionId,
        lastHeartbeatAt = lastHeartbeatAt,
        status = "recording",
    )
}
