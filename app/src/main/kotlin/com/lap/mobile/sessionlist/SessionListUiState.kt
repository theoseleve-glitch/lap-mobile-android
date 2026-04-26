package com.lap.mobile.sessionlist

/**
 * Renderable row in the SessionList screen (T226 scaffold).
 *
 * Held as a UI-layer record (not the [com.lap.mobile.data.SessionEntity]
 * directly) so the ViewModel can shape values into display-ready
 * form once T227 wires in the repository — sample-count gets a
 * thousands separator, startedAtIso becomes the user's locale
 * date format, etc. Keeping this record separate from the Room
 * entity stops display logic from leaking into the persistence
 * layer.
 *
 * `startedAtIso` is nullable because a session can be ARMED but
 * never actually transition to RECORDING (preflight failure,
 * user backed out before tapping start). The list still wants to
 * show those rows so the user can finalize/discard them via the
 * recovery flow (T058) — a missing start time renders as "—" or
 * "Never started" depending on which the eventual screen design
 * picks.
 */
data class SessionListRow(
    val id: String,
    val state: String,
    val sampleCount: Int,
    val startedAtIso: String?,
)

/**
 * UI state for the SessionList screen (T226 scaffold). Cold-launch
 * defaults are empty list + not loading + no error so the eventual
 * Compose screen has a stable pre-data render — the same rationale
 * as [com.lap.mobile.dashboard.DashboardUiState] zeros-not-nulls
 * decision: defaults represent a working empty state, not "we
 * don't know yet".
 *
 * Three concurrent flags rather than a sealed Loaded/Loading/Error
 * union because the screen has to distinguish "loaded with prior
 * data + currently refreshing" (sessions populated + isLoading
 * true) from "first-time load" (sessions empty + isLoading true)
 * — the union form would force a choice between the two and the
 * cleaner separation is via parallel flags.
 *
 * `errorMessage` is a free-form String not a sealed [com.lap.mobile.core.DomainError]
 * — the VM stringifies the error before exposing it because the UI
 * layer renders text not enum cases. Keeping the typed error in
 * a future field would let the screen pick locale-aware messages
 * but T226 scope is the scaffold, not localised error rendering.
 */
data class SessionListUiState(
    val sessions: List<SessionListRow> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
