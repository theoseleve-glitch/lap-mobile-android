package com.lap.mobile.capture

/**
 * Preflight check that the user has picked a track (T090). Lap
 * detection (T210+ server-side), sector splits, and most coaching
 * rules key off `trackId` — running a session without one means the
 * server-side analysis layer can't bind the sample timeline to a
 * known geometry and the recording is effectively orphaned.
 *
 * The actual "selected track" UI + persistence lands in Block J;
 * this check just polls a [SelectedTrackProvider] for the current
 * value at preflight time. A `null` track id is the single failure
 * mode — there's no "wrong track" check here because the user is
 * the source of truth.
 */
class TrackSelectionCheck(
    private val provider: SelectedTrackProvider,
) : PreflightCheck {
    override suspend fun failureReason(): String? {
        return if (provider.selectedTrackId() != null) null else REASON_TRACK_NOT_SELECTED
    }

    companion object {
        const val REASON_TRACK_NOT_SELECTED: String = "track_not_selected"
    }
}
