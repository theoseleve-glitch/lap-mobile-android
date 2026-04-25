package com.lap.mobile.capture

import com.lap.schemas.v1.UUID7
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val FIXTURE_TRACK_ID: UUID7 = "0194cccc-cccc-7ccc-cccc-cccccccccccc"

/**
 * Unit tests for [TrackSelectionCheck] (T090). Lap detection,
 * sector splits, and most coaching rules key off `trackId` — running
 * a session without a track means the server-side analysis layer
 * can't bind sample timelines to a known geometry, so this is a
 * hard preflight block.
 */
class TrackSelectionCheckTest {
    @Test
    fun null_track_id_returns_track_not_selected_reason() = runTest {
        val provider = SelectedTrackProvider { null }
        val check = TrackSelectionCheck(provider)

        assertEquals(TrackSelectionCheck.REASON_TRACK_NOT_SELECTED, check.failureReason())
    }

    @Test
    fun set_track_id_returns_null() = runTest {
        val provider = SelectedTrackProvider { FIXTURE_TRACK_ID }
        val check = TrackSelectionCheck(provider)

        assertNull(check.failureReason())
    }
}
