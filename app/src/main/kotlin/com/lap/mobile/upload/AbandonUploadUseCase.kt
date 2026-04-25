package com.lap.mobile.upload

import com.lap.mobile.data.SessionDao
import com.lap.schemas.v1.UUID7
import javax.inject.Inject

/**
 * Outcome of a user-initiated abandon against a local session.
 *
 * - [Removed]: the session row existed and was deleted; SQLite's
 *   `ON DELETE CASCADE` on [com.lap.mobile.data.UploadQueueEntity]
 *   sweeps the queue row in the same transaction so the worker
 *   stops re-attempting it.
 * - [NotFound]: no session row matched. Most likely a concurrent
 *   abandon (duplicate tap, parallel reconciler) or a completed
 *   session whose row was swept server-side. Surfacing this lets
 *   the caller warn the user instead of silently no-op'ing a tap
 *   on a destructive button.
 */
enum class AbandonOutcome { Removed, NotFound }

/**
 * Hand-deletes a session and (via FK CASCADE) its upload_queue row.
 *
 * Triggered by the user tapping "Abandon" on a dashboard entry,
 * typically after an upload has hit `failed_permanent` (T155) and
 * the user no longer wants the bytes preserved or re-uploaded.
 *
 * Single-statement design intentional: the cascade FK on
 * [com.lap.mobile.data.UploadQueueEntity.sessionId] means we don't
 * need to (and must not) issue a second DELETE against upload_queue
 * — SQLite does it atomically with the parent delete, eliminating
 * the "queue row left dangling because the second statement failed"
 * race that a manual two-step delete would carry.
 *
 * Files-on-disk cleanup is *not* this use case's job. The buffer file
 * for an abandoned session is discoverable via
 * [com.lap.mobile.data.OrphanTempFileReconciler] — a session row gone
 * from the DB but a `.bundle`/`.buffer` file still on disk is exactly
 * what its `findOrphans()` returns. Startup reconciliation (or a
 * future cleanup worker) reclaims those bytes; T162's contract here
 * is queue-removal only.
 */
class AbandonUploadUseCase @Inject constructor(
    private val sessionDao: SessionDao,
) {
    suspend fun abandon(sessionId: UUID7): AbandonOutcome {
        val rowsDeleted = sessionDao.deleteById(sessionId)
        return if (rowsDeleted > 0) AbandonOutcome.Removed else AbandonOutcome.NotFound
    }
}
