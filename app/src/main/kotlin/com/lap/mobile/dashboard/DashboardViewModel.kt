package com.lap.mobile.dashboard

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel for the live-dashboard screen (T192 scaffold).
 *
 * Per CLAUDE.md "Every ViewModel has a StateFlow exposing UiState":
 * exposes [state] as a [StateFlow] of [DashboardUiState]. Late
 * subscribers (screen recomposition after rotation, navigation back
 * to dashboard) get the current value immediately — what they care
 * about is "what does the dashboard show right now", not a history.
 *
 * No methods yet — T192's "compiles" acceptance is the scaffold
 * only. T193+ adds field-specific projection (speed source, lap
 * timer, etc.) and T196+ wires the actual data sources (capture
 * pipeline, session coordinator). The shape of [state] is the seam
 * those tasks plug into.
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
}
