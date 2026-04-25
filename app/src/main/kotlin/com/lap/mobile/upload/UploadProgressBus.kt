package com.lap.mobile.upload

import com.lap.schemas.v1.UUID7
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-chunk upload progress for one session (T153). Emitted by the
 * upload worker / orchestrator as the multipart write streams chunks
 * to the server; consumed by the dashboard / UploadStatus UI to render
 * a "uploading X of Y" indicator.
 *
 * Invariants enforced in the init block: chunk counts never go
 * negative, and `chunksSent` cannot exceed `chunksTotal` — an
 * out-of-range value would surface as a malformed progress bar in
 * the UI, so it's better to fail loudly at the source.
 */
data class UploadProgress(
    val sessionId: UUID7,
    val chunksSent: Int,
    val chunksTotal: Int,
) {
    init {
        require(chunksSent >= 0) { "chunksSent must be >= 0, got $chunksSent" }
        require(chunksTotal >= 0) { "chunksTotal must be >= 0, got $chunksTotal" }
        require(chunksSent <= chunksTotal) {
            "chunksSent ($chunksSent) must be <= chunksTotal ($chunksTotal)"
        }
    }
}

/**
 * Singleton hub that owns the per-session [UploadProgress] state and
 * exposes it as a [StateFlow] so the UI can collect with
 * `collectAsStateWithLifecycle()` (T153).
 *
 * The state is a `Map<UUID7, UploadProgress>` rather than a single
 * latest event so that:
 *   * parallel uploads (multiple sessions in flight after a long
 *     offline stretch) don't clobber each other's progress; and
 *   * the UI can ask "what's the current progress for *this*
 *     session?" with a simple `state.value[sessionId]` lookup
 *     instead of filtering an event stream.
 *
 * StateFlow rather than SharedFlow so a late subscriber (e.g., the
 * user navigates back to the dashboard mid-upload) immediately gets
 * the current snapshot without missing prior emissions —
 * mirrors the rationale documented on
 * [com.lap.mobile.capture.ble.BleConnectionStateHolder].
 */
@Singleton
class UploadProgressBus @Inject constructor() {
    private val _state: MutableStateFlow<Map<UUID7, UploadProgress>> =
        MutableStateFlow(emptyMap())
    val state: StateFlow<Map<UUID7, UploadProgress>> = _state.asStateFlow()

    fun report(progress: UploadProgress) {
        _state.update { it + (progress.sessionId to progress) }
    }
}
