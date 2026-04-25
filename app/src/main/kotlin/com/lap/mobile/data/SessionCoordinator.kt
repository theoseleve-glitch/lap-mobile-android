package com.lap.mobile.data

import com.lap.mobile.capture.CaptureOrchestrator
import com.lap.mobile.capture.PreflightGate
import com.lap.mobile.capture.PreflightStatus
import com.lap.mobile.core.DomainError
import com.lap.mobile.core.Result
import com.lap.schemas.v1.SourceTag
import com.lap.schemas.v1.UUID7
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant

/**
 * Wires the recording lifecycle (T076):
 *
 *  - [RecordingState] machine (T075) → state transitions
 *  - [CaptureOrchestrator] (T071) → samples flow
 *  - [JournalWriter] (T054) → 5 s heartbeats so the reconciler (T055)
 *    can recover this session if the app dies
 *  - [SessionDao] → durable row reflecting the live state for
 *    list/recovery UIs
 *
 * The user-facing flow is `arm(sid)` → preflight (T077 gates this) →
 * `start(scope)` → `stop()`. The coordinator caches the
 * [SessionEntity] across transitions so each upsert can copy-with-
 * update without an interleaved read; that closes the read/write
 * race a `findById → upsert` pattern would open.
 *
 * `start` takes a [CoroutineScope] rather than holding one — the
 * foreground service's lifecycle owns the scope in production
 * ([com.lap.mobile.capture.CaptureForegroundService]); `runTest`'s
 * `backgroundScope` owns it in tests. `stop()` ends at FINALIZING;
 * the bundle writer (T131) flips to COMPLETED when the bundle is
 * sealed.
 */
class SessionCoordinator(
    private val sessionDao: SessionDao,
    private val orchestrator: CaptureOrchestrator,
    private val journalWriter: JournalWriter,
    private val clock: Clock,
    private val preflight: PreflightGate,
) {
    private val _state: MutableStateFlow<RecordingState> = MutableStateFlow(RecordingState.IDLE)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _sampleCount: MutableStateFlow<Int> = MutableStateFlow(0)
    val sampleCount: StateFlow<Int> = _sampleCount.asStateFlow()

    /**
     * Fires once per successful [stop] (T078). `replay = 0` keeps it
     * an *event* not a state — late subscribers don't see stale stops.
     * `extraBufferCapacity = 1` lets `tryEmit` succeed in `stop` even
     * with no subscriber attached.
     */
    private val _endEvents: MutableSharedFlow<SessionEndEvent> =
        MutableSharedFlow(extraBufferCapacity = 1)
    val endEvents: SharedFlow<SessionEndEvent> = _endEvents.asSharedFlow()

    private var currentSession: SessionEntity? = null
    private var captureJob: Job? = null
    private var journalJob: Job? = null

    suspend fun arm(sessionId: UUID7): Result<Unit, DomainError> {
        val current = _state.value
        if (!current.canTransitionTo(RecordingState.ARMED)) {
            return Result.Err(
                DomainError.Validation(field = "state", message = "cannot arm from $current"),
            )
        }
        _sampleCount.value = 0
        val entity = SessionEntity(
            id = sessionId,
            state = RecordingState.ARMED.wireValue,
            source = SourceTag.REAL,
        )
        sessionDao.upsert(entity)
        currentSession = entity
        _state.value = RecordingState.ARMED
        return Result.Ok(Unit)
    }

    suspend fun start(scope: CoroutineScope): Result<Unit, DomainError> {
        val current = _state.value
        if (!current.canTransitionTo(RecordingState.RECORDING)) {
            return Result.Err(
                DomainError.Validation(field = "state", message = "cannot start from $current"),
            )
        }
        val cached = currentSession ?: return Result.Err(
            DomainError.Validation(field = "session", message = "no session armed"),
        )

        // T077: preflight (Block E) must pass before ARMED → RECORDING.
        // State-machine guard runs first so an obviously-invalid call doesn't
        // pay the cost of preflight (which can include I/O for permissions /
        // GPS quality / storage etc).
        when (val status = preflight.check()) {
            is PreflightStatus.Ready -> Unit
            is PreflightStatus.NotReady -> return Result.Err(
                DomainError.Validation(
                    field = "preflight",
                    message = "preflight not ready: ${status.reasons.joinToString(", ")}",
                ),
            )
        }

        val updated = cached.copy(
            state = RecordingState.RECORDING.wireValue,
            startedAt = Instant.now(clock),
        )
        sessionDao.upsert(updated)
        currentSession = updated
        _state.value = RecordingState.RECORDING

        captureJob = scope.launch {
            orchestrator.samples().collect { _sampleCount.update { count -> count + 1 } }
        }
        journalJob = journalWriter.start(scope, cached.id) { _sampleCount.value.toLong() }

        return Result.Ok(Unit)
    }

    suspend fun stop(): Result<Unit, DomainError> {
        val current = _state.value
        if (!current.canTransitionTo(RecordingState.FINALIZING)) {
            return Result.Err(
                DomainError.Validation(field = "state", message = "cannot stop from $current"),
            )
        }
        val cached = currentSession ?: return Result.Err(
            DomainError.Validation(field = "session", message = "no current session"),
        )

        captureJob?.cancelAndJoin()
        journalJob?.cancelAndJoin()
        captureJob = null
        journalJob = null

        val endedAt = Instant.now(clock)
        val finalSampleCount = _sampleCount.value
        val updated = cached.copy(
            state = RecordingState.FINALIZING.wireValue,
            endedAt = endedAt,
            sampleCount = finalSampleCount,
        )
        sessionDao.upsert(updated)
        currentSession = updated
        _state.value = RecordingState.FINALIZING

        // T078: notify downstream (bundle writer T131, analytics) only AFTER
        // the state flip + DAO write succeed — consumers see a coherent post-stop
        // snapshot. Failed stops short-circuit above and never reach this emit.
        _endEvents.tryEmit(
            SessionEndEvent(
                sessionId = cached.id,
                endedAt = endedAt,
                sampleCount = finalSampleCount,
            ),
        )

        return Result.Ok(Unit)
    }
}
