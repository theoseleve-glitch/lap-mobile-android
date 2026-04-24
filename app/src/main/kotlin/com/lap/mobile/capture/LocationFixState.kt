package com.lap.mobile.capture

/**
 * Public UiState for "do we have a GNSS fix?" (T067). Renders into the
 * recording screen as a status pill so the user can tell whether the
 * app is still receiving samples — losing a fix mid-session is the
 * single most common reason laps don't reconcile against the track
 * (ADR-012 ingestion treats missing-data gaps as soft errors).
 *
 * Three discrete states only: a "fix-quality" tier (good/poor/none)
 * is intentionally out of scope for T067 — that's the per-sample
 * accuracy threshold T091 enforces at preflight.
 */
sealed class LocationFixState {
    /** No sample has arrived yet — receiver is still acquiring. */
    data object Searching : LocationFixState()

    /** Receiver is producing samples within the stale threshold. */
    data object Acquired : LocationFixState()

    /** No sample within the stale threshold — fix is presumed lost. */
    data object Lost : LocationFixState()
}
