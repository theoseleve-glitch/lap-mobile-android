package com.lap.mobile.capture.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DISCONNECT_TS_NS: Long = 1_700_000_000_000_000_000L

class RaceboxFallbackControllerTest {
    @Test
    fun handles_final_disconnect_by_journalling_with_provided_timestamp() {
        val journalled = mutableListOf<Long>()
        val controller = RaceboxFallbackController(
            journal = SessionGapJournal { journalled.add(it) },
            sourceSwitch = { /* swallow */ },
        )

        controller.handleFinalDisconnect(DISCONNECT_TS_NS)

        assertEquals(listOf(DISCONNECT_TS_NS), journalled)
    }

    @Test
    fun handles_final_disconnect_by_switching_active_source_to_phone() {
        var switched = false
        val controller = RaceboxFallbackController(
            journal = SessionGapJournal { },
            sourceSwitch = { switched = true },
        )

        controller.handleFinalDisconnect(DISCONNECT_TS_NS)

        assertTrue(switched)
    }

    @Test
    fun journals_gap_before_switching_source_so_failure_after_switch_still_keeps_marker() {
        val sequence = mutableListOf<String>()
        val controller = RaceboxFallbackController(
            journal = SessionGapJournal { sequence.add("journal") },
            sourceSwitch = { sequence.add("switch") },
        )

        controller.handleFinalDisconnect(DISCONNECT_TS_NS)

        assertEquals(listOf("journal", "switch"), sequence)
    }
}
