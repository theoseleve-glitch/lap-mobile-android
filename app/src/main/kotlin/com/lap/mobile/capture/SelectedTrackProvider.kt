package com.lap.mobile.capture

import com.lap.schemas.v1.UUID7

/**
 * Source-of-truth for the currently selected track (T090). The full
 * implementation (settings-store backed, observable via Flow when the
 * track-picker UI lands) is a Block J task; for the preflight gate
 * here we only need a single-shot query, which keeps this interface
 * small and easy to fake in tests.
 *
 * Returning `UUID7?` rather than `String?` so an inadvertent caller
 * can't pass an arbitrary string — the recovery modal preview shows
 * the v7 string shape these values must conform to.
 */
fun interface SelectedTrackProvider {
    suspend fun selectedTrackId(): UUID7?
}
