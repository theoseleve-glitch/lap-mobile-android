package com.lap.mobile.capture

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [RaceboxConnectionCheck] (T092). Like
 * [BluetoothCheck], this is a no-op when RaceBox mode is off — only
 * relevant when the user has actually opted into RaceBox capture.
 */
class RaceboxConnectionCheckTest {
    @Test
    fun racebox_disabled_returns_null_regardless_of_connection_state() = runTest {
        val status = RaceboxConnectionStatus { false }
        val check = RaceboxConnectionCheck(status = status, raceboxModeEnabled = { false })

        assertNull(check.failureReason())
    }

    @Test
    fun racebox_enabled_and_connected_returns_null() = runTest {
        val status = RaceboxConnectionStatus { true }
        val check = RaceboxConnectionCheck(status = status, raceboxModeEnabled = { true })

        assertNull(check.failureReason())
    }

    @Test
    fun racebox_enabled_and_disconnected_returns_not_connected_reason() = runTest {
        val status = RaceboxConnectionStatus { false }
        val check = RaceboxConnectionCheck(status = status, raceboxModeEnabled = { true })

        assertEquals(
            RaceboxConnectionCheck.REASON_RACEBOX_NOT_CONNECTED,
            check.failureReason(),
        )
    }
}
