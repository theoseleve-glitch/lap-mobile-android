package com.lap.mobile.sessionlist

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel for the SessionList screen (T226 scaffold — referenced
 * by [com.lap.mobile.ui.navigation.Routes.SessionList] T024 destination).
 *
 * Per CLAUDE.md "Every ViewModel has a StateFlow exposing UiState":
 * exposes [state] as a [StateFlow] of [SessionListUiState]. Late
 * subscribers (screen recomposition after rotation, navigation back
 * from Dashboard) get the current value immediately.
 *
 * Three lifecycle methods (onLoadStarted, onSessionsLoaded, onLoadFailed)
 * model the typical fetch state machine. They're public on the VM
 * so JVM tests can drive transitions directly while T227 wires in
 * the actual repository — same shape T210 used for
 * [com.lap.mobile.dashboard.DashboardViewModel.onRecordingStarted].
 *
 * Constructor is parameterless for now (Hilt @Inject). T227 will
 * inject a SessionRepository and the VM will collect from a
 * Flow<List<SessionEntity>> exposed by [com.lap.mobile.data.SessionDao];
 * once that lands the public lifecycle methods become private (or
 * disappear if the Flow drives state directly).
 *
 * @HiltViewModel + @Inject so the Compose host can obtain it via
 * `hiltViewModel()` once Compose-side wiring lands. JVM tests
 * instantiate directly to keep the gate cheap.
 */
@HiltViewModel
class SessionListViewModel
@Inject
constructor() : ViewModel() {
    private val internalState: MutableStateFlow<SessionListUiState> =
        MutableStateFlow(SessionListUiState())
    val state: StateFlow<SessionListUiState> = internalState.asStateFlow()

    /**
     * Begin a refresh cycle. Sets isLoading=true and clears any
     * prior errorMessage. Existing rows are kept so the screen can
     * display "loaded data + spinner" for a swipe-to-refresh UX.
     */
    fun onLoadStarted() {
        internalState.value = internalState.value.copy(
            isLoading = true,
            errorMessage = null,
        )
    }

    /**
     * Replace the displayed rows with the freshly-loaded list.
     * Clears isLoading and any prior errorMessage.
     */
    fun onSessionsLoaded(sessions: List<SessionListRow>) {
        internalState.value = internalState.value.copy(
            sessions = sessions,
            isLoading = false,
            errorMessage = null,
        )
    }

    /**
     * Record a load failure. Clears isLoading + sets errorMessage
     * but KEEPS the prior session rows so the screen can show
     * "stale data + error banner" — losing previously-rendered rows
     * because the refresh failed would surprise the user mid-scroll.
     */
    fun onLoadFailed(message: String) {
        internalState.value = internalState.value.copy(
            isLoading = false,
            errorMessage = message,
        )
    }
}
