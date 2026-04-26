package com.lap.mobile.dashboard

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel for the live-dashboard screen (T192 scaffold + T210
 * recording-lifecycle methods).
 *
 * Per CLAUDE.md "Every ViewModel has a StateFlow exposing UiState":
 * exposes [state] as a [StateFlow] of [DashboardUiState]. Late
 * subscribers (screen recomposition after rotation, navigation back
 * to dashboard) get the current value immediately — what they care
 * about is "what does the dashboard show right now", not a history.
 *
 * [onRecordingStarted] / [onRecordingStopped] (T210) are the
 * minimum-viable lifecycle API the dashboard composition pass needs
 * for cross-policy integration testing. They flip the
 * [DashboardUiState.recordingStripState] field, which T204
 * (BackInterceptPolicy), T205 (ScreenWakePolicy), and T206
 * (OrientationLockPolicy) all read as their lifecycle source of
 * truth.
 *
 * In production the actual lifecycle calls will be triggered by the
 * capture-pipeline foreground service (T211+) when the user taps the
 * Start/Stop controls. For now the methods are public on the
 * ViewModel so the integration test can drive transitions directly
 * — same shape T173+ used for SecureTokenStorage when callers were
 * tests-only.
 *
 * @HiltViewModel + @Inject so the Compose host can obtain it via
 * `hiltViewModel()` once Compose-side wiring lands. JVM tests
 * instantiate directly (no constructor args) — Hilt graph integrity
 * is a separate concern from VM logic.
 */
@HiltViewModel
class DashboardViewModel
@Inject
constructor() : ViewModel() {
    private val internalState: MutableStateFlow<DashboardUiState> =
        MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = internalState.asStateFlow()

    /**
     * Transition the dashboard to the active-recording lifecycle.
     * Emits [DashboardUiState.recordingStripState] = Recording so
     * downstream policies (back-intercept, screen-wake, orientation)
     * apply their session-mode verdicts.
     *
     * @param sessionElapsedMs the wall-clock elapsed time the strip
     *   should display. Defaults to 0 for the typical "user just
     *   tapped record" call site; tests can pass non-zero values
     *   to exercise mid-session state.
     */
    fun onRecordingStarted(sessionElapsedMs: Long = 0L) {
        internalState.value = internalState.value.copy(
            recordingStripState = RecordingStripState.Recording(sessionElapsedMs = sessionElapsedMs),
        )
    }

    /**
     * Transition the dashboard back to the idle lifecycle.
     * Emits [DashboardUiState.recordingStripState] = Idle so
     * downstream policies release their session-mode locks
     * (back gesture passes through, screen timeout resumes,
     * orientation unlocks).
     */
    fun onRecordingStopped() {
        internalState.value = internalState.value.copy(
            recordingStripState = RecordingStripState.Idle,
        )
    }
}
