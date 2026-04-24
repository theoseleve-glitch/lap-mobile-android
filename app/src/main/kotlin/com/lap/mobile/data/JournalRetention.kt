package com.lap.mobile.data

import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Default retention window. Journal rows older than this are eligible
 * for deletion — long enough that a user who forgot the app for a few
 * days can still recover, short enough that the journal table doesn't
 * grow unbounded across hundreds of crashed sessions.
 */
private const val DEFAULT_RETENTION_DAYS: Long = 7L

/**
 * Auto-clean for stale journal rows (T060). Runs after the user
 * confirms via the recovery modal — never silently — so we don't
 * destroy rows the user could have recovered with Finalize-from-
 * journal (T059). Threshold is strict (`<`, not `<=`), matching
 * [JournalReconciler]'s convention: a row exactly at the 7-day
 * boundary is preserved.
 *
 * The use case is pure orchestration on top of
 * [JournalDao.deleteOlderThan] — testable off [FixedClock] without
 * Room or an Android runtime. Retention period is injectable so
 * dev/staging flavors can run a tighter cycle.
 */
class JournalRetention(
    private val dao: JournalDao,
    private val clock: Clock,
    private val retention: Duration = Duration.ofDays(DEFAULT_RETENTION_DAYS),
) {
    suspend fun cleanup(confirmedByUser: Boolean): Int {
        if (!confirmedByUser) return 0
        val cutoff = Instant.now(clock).minus(retention)
        return dao.deleteOlderThan(cutoff)
    }
}
