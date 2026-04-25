package com.lap.mobile.capture

/**
 * Outcome of a preflight check (T077 gate / T086+ full service).
 * Sealed so callers pattern-match exhaustively — the recording UI
 * (T097) needs to render *why* a session can't start, not just a red
 * X. [NotReady.reasons] are open-vocabulary strings keyed by the
 * specific check that failed (`location_permission`, `gps_accuracy`,
 * `bluetooth`, `track_selected`, …) so a future taxonomy change is a
 * single-site edit and the gate doesn't need to re-derive the list.
 */
sealed class PreflightStatus {
    data object Ready : PreflightStatus()

    data class NotReady(val reasons: List<String>) : PreflightStatus()
}
