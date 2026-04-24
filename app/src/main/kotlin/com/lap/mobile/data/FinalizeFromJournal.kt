package com.lap.mobile.data

import com.lap.mobile.core.DomainError
import com.lap.mobile.core.Result
import com.lap.schemas.v1.UUID7

/**
 * State literals for the recording state machine (T075 promotes to a
 * typed enum). Kept next to the use case so the two transitions
 * finalize drives — `completed` on success, `failed` on finalizer
 * error — aren't magic strings.
 */
private const val STATE_COMPLETED: String = "completed"
private const val STATE_FAILED: String = "failed"

/**
 * Runs the Finalize-from-journal recovery path the UI triggers from
 * T058's recovery modal. Orchestration only:
 *
 *  1. Load the [SessionEntity] (NotFound if absent — the user picked
 *     an id the reconciler surfaced but that was since deleted).
 *  2. Load the [SessionJournalEntity] (NotFound if absent — a session
 *     that never reached RECORDING won't have a journal row, and
 *     there's nothing to finalize from).
 *  3. Hand both to the [BundleFinalizer] (T131's BundleV2Writer in
 *     production; an abstract collaborator here so T059 is testable
 *     without disk).
 *  4. On success, stamp the session with `completed` + metadata from
 *     the finalized bundle. On finalizer error, flip to `failed` and
 *     propagate the [DomainError] up — the modal can show the reason
 *     and the user can choose to Discard.
 *
 * The use case never touches the buffer file directly; that's the
 * finalizer's job.
 */
class FinalizeFromJournal(
    private val sessionDao: SessionDao,
    private val journalDao: JournalDao,
    private val bundleFinalizer: BundleFinalizer,
) {
    suspend fun run(sessionId: UUID7): Result<BundleFinalizer.FinalizedBundle, DomainError> {
        val session = sessionDao.findById(sessionId)
            ?: return Result.Err(DomainError.NotFound(what = "session:$sessionId"))
        val journal = journalDao.findBySessionId(sessionId)
            ?: return Result.Err(DomainError.NotFound(what = "journal:$sessionId"))
        return when (val result = bundleFinalizer.finalize(session, journal)) {
            is Result.Ok -> {
                sessionDao.upsert(
                    session.copy(
                        state = STATE_COMPLETED,
                        endedAt = result.value.endedAt,
                        fileUri = result.value.fileUri,
                        sampleCount = result.value.sampleCount,
                    ),
                )
                result
            }
            is Result.Err -> {
                sessionDao.upsert(session.copy(state = STATE_FAILED))
                result
            }
        }
    }
}
