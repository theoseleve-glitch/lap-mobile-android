package com.lap.mobile.capture.ble

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EXPECTED_MAX_ATTEMPTS: Int = 3

class RaceboxReconnectPolicyTest {
    @Test
    fun returns_true_and_stops_after_first_successful_attempt() = runTest {
        val attempts = mutableListOf<Int>()
        val policy = RaceboxReconnectPolicy(
            BleReconnectAttempt { attempt ->
                attempts.add(attempt)
                true
            },
        )

        val result = policy.reconnect()

        assertTrue(result)
        assertEquals(listOf(1), attempts)
    }

    @Test
    fun returns_true_when_third_attempt_succeeds() = runTest {
        var calls = 0
        val policy = RaceboxReconnectPolicy(
            BleReconnectAttempt {
                calls++
                calls == EXPECTED_MAX_ATTEMPTS
            },
        )

        val result = policy.reconnect()

        assertTrue(result)
        assertEquals(EXPECTED_MAX_ATTEMPTS, calls)
    }

    @Test
    fun returns_false_after_three_failed_attempts() = runTest {
        var calls = 0
        val policy = RaceboxReconnectPolicy(
            BleReconnectAttempt {
                calls++
                false
            },
        )

        val result = policy.reconnect()

        assertFalse(result)
        assertEquals(EXPECTED_MAX_ATTEMPTS, calls)
    }

    @Test
    fun never_attempts_more_than_three_times() = runTest {
        var calls = 0
        val policy = RaceboxReconnectPolicy(
            BleReconnectAttempt {
                calls++
                false
            },
        )

        policy.reconnect()
        // A second invocation must also obey the same cap (state-free policy).
        policy.reconnect()

        assertEquals(EXPECTED_MAX_ATTEMPTS * 2, calls)
    }

    @Test
    fun passes_one_indexed_attempt_number_to_connector() = runTest {
        val attempts = mutableListOf<Int>()
        val policy = RaceboxReconnectPolicy(
            BleReconnectAttempt { attempt ->
                attempts.add(attempt)
                false
            },
        )

        policy.reconnect()

        assertEquals(listOf(1, 2, 3), attempts)
    }
}
