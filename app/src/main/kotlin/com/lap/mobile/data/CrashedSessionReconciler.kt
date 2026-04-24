package com.lap.mobile.data

/**
 * State literals for the recording state machine (T075 promotes these
 * to a typed enum). Kept here as single-source constants so the
 * reconciler and the state machine don't drift on spellings.
 */
private const val STATE_RECORDING: String = "recording"
private const val STATE_FINALIZING: String = "finalizing"

/**
 * Third startup reconciler (paired with [JournalReconciler] T055 and
 * [OrphanTempFileReconciler] T056). This one finds SessionEntity rows
 * stuck in `recording` or `finalizing` at launch — the app process
 * died mid-transition, so the user should be offered recovery (T058
 * modal → T059 Finalize-from-journal) per ADR-012.
 *
 * Completed/failed sessions are terminal and never recoverable.
 * Idle/armed sessions are pre-capture — no buffer file yet, nothing
 * to recover.
 *
 * Implementation-wise the reconciler does two DAO reads and unions
 * the result. A `listByStatuses(states)` DAO method would be one call
 * but churn on SessionDao isn't worth 1 ms at startup.
 */
class CrashedSessionReconciler(
    private val sessionDao: SessionDao,
) {
    suspend fun findRecoverable(): List<SessionEntity> =
        sessionDao.listByStatus(STATE_RECORDING) + sessionDao.listByStatus(STATE_FINALIZING)
}
