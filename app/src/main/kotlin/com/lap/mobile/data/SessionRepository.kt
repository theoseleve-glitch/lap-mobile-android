package com.lap.mobile.data

import com.lap.mobile.core.DomainError
import com.lap.mobile.core.Result
import com.lap.schemas.v1.UUID7
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin repository wrapping [SessionDao] (T227 scaffold).
 *
 * Per CLAUDE.md "Every repository returns Result<T, DomainError>":
 * suspending methods return [Result] and the [observeByState] Flow
 * emits Result-wrapped lists so a downstream collector failure
 * surfaces as a single Err rather than tearing down the entire
 * subscription chain.
 *
 * The repo intentionally does NOT shape entities into UI rows
 * here — [com.lap.mobile.sessionlist.SessionListViewModel] (T226)
 * owns the entity → row mapping at the VM boundary because the
 * formatting decisions (locale-aware date, thousands-separated
 * sample count) are UI concerns. Keeping the repo entity-shaped
 * means a future second consumer (e.g., a WorkManager task that
 * needs the raw [SessionEntity.fileUri]) doesn't pay the
 * UI-formatting cost.
 *
 * Future T228+ may expand this to include the journal-recovery
 * methods T055/T056 use directly today; for now those bypass the
 * repo and call SessionDao directly because the recovery flow
 * predates this repo by ~150 tasks.
 */
@Singleton
class SessionRepository
@Inject
constructor(
    private val sessionDao: SessionDao,
) {
    /**
     * Reactive stream of sessions in the given [state]. Each emission
     * is the full snapshot Room produces — collectors should diff
     * against their last value rather than re-rendering the whole
     * list every tick (Compose handles this via `key=` on LazyColumn
     * items).
     *
     * Errors from the underlying DAO Flow are mapped to
     * [DomainError.Io] and emitted as a single [Result.Err] rather
     * than throwing — the SessionList screen can render an inline
     * error banner without losing prior data.
     */
    fun observeByState(state: RecordingState): Flow<Result<List<SessionEntity>, DomainError>> =
        sessionDao.observeByStatus(state.wireValue)
            .map { rows -> Result.Ok(rows) as Result<List<SessionEntity>, DomainError> }
            .catch { throwable -> emit(Result.Err(DomainError.Io(throwable.message.orEmpty()))) }

    /**
     * Look up a session by id. Returns `Ok(null)` when the row
     * doesn't exist (reserves [DomainError.NotFound] for the
     * delete path where "row gone" is genuinely an error).
     */
    suspend fun findById(id: UUID7): Result<SessionEntity?, DomainError> = runCatchingDao {
        sessionDao.findById(id)
    }

    /**
     * Hard-delete a session. Returns [DomainError.NotFound] when the
     * row was already gone (concurrent T162 abandon, server-driven
     * completion sweep) so the caller can choose between "treat as
     * idempotent success" and "surface to user as already-removed".
     */
    suspend fun delete(id: UUID7): Result<Unit, DomainError> = runCatchingDao {
        val deleted = sessionDao.deleteById(id)
        if (deleted == 0) {
            return Result.Err(DomainError.NotFound("session:$id"))
        }
    }
}

@Suppress("TooGenericExceptionCaught")
private inline fun <T> runCatchingDao(block: () -> T): Result<T, DomainError> {
    return try {
        Result.Ok(block())
    } catch (e: Exception) {
        Result.Err(DomainError.Io(e.message.orEmpty()))
    }
}
