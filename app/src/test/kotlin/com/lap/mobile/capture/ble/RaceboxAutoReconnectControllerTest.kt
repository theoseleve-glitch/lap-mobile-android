package com.lap.mobile.capture.ble

import com.lap.mobile.data.LastUsedRacebox
import com.lap.mobile.data.LastUsedRaceboxLookup
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

private const val SAVED_ADDRESS: String = "AA:BB:CC:DD:EE:01"
private const val SAVED_NAME: String = "RaceBox Saved"
private val SAVED_AT: Instant = Instant.parse("2026-04-25T14:30:12Z")
private val SAVED_DEVICE: LastUsedRacebox =
    LastUsedRacebox(address = SAVED_ADDRESS, name = SAVED_NAME, savedAt = SAVED_AT)
private const val EXPECTED_MAX_ATTEMPTS: Int = 3

class RaceboxAutoReconnectControllerTest {
    @Test
    fun returns_no_saved_device_when_lookup_returns_null() = runTest {
        val controller = RaceboxAutoReconnectController(
            lookup = LastUsedRaceboxLookup { null },
            attempt = AddressedBleConnectAttempt { _, _ -> error("must not attempt without saved device") },
        )

        val outcome = controller.attemptAutoReconnect()

        assertEquals(AutoReconnectOutcome.NoSavedDevice, outcome)
    }

    @Test
    fun returns_connected_with_saved_device_on_first_successful_attempt() = runTest {
        val controller = RaceboxAutoReconnectController(
            lookup = LastUsedRaceboxLookup { SAVED_DEVICE },
            attempt = AddressedBleConnectAttempt { _, _ -> true },
        )

        val outcome = controller.attemptAutoReconnect()

        assertEquals(AutoReconnectOutcome.Connected(SAVED_DEVICE), outcome)
    }

    @Test
    fun returns_failed_with_saved_device_after_three_failures() = runTest {
        var calls = 0
        val controller = RaceboxAutoReconnectController(
            lookup = LastUsedRaceboxLookup { SAVED_DEVICE },
            attempt = AddressedBleConnectAttempt { _, _ ->
                calls++
                false
            },
        )

        val outcome = controller.attemptAutoReconnect()

        assertEquals(AutoReconnectOutcome.Failed(SAVED_DEVICE), outcome)
        assertEquals(EXPECTED_MAX_ATTEMPTS, calls)
    }

    @Test
    fun passes_saved_device_address_to_every_attempt() = runTest {
        val seenAddresses = mutableListOf<String>()
        val controller = RaceboxAutoReconnectController(
            lookup = LastUsedRaceboxLookup { SAVED_DEVICE },
            attempt = AddressedBleConnectAttempt { address, _ ->
                seenAddresses.add(address)
                false
            },
        )

        controller.attemptAutoReconnect()

        assertEquals(listOf(SAVED_ADDRESS, SAVED_ADDRESS, SAVED_ADDRESS), seenAddresses)
    }

    @Test
    fun stops_at_first_success_within_retry_window() = runTest {
        var calls = 0
        val controller = RaceboxAutoReconnectController(
            lookup = LastUsedRaceboxLookup { SAVED_DEVICE },
            attempt = AddressedBleConnectAttempt { _, _ ->
                calls++
                calls == 2
            },
        )

        val outcome = controller.attemptAutoReconnect()

        assertEquals(AutoReconnectOutcome.Connected(SAVED_DEVICE), outcome)
        assertEquals(2, calls)
    }
}
