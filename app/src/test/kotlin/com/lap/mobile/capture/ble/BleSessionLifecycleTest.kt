package com.lap.mobile.capture.ble

import com.lap.mobile.capture.CaptureForegroundCommand
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BleSessionLifecycleTest {
    @Test
    fun start_command_invokes_session_start_action() = runTest {
        val sequence = mutableListOf<String>()
        val lifecycle = BleSessionLifecycle(
            onSessionStart = BleSessionAction { sequence.add("start") },
            onSessionStop = BleSessionAction { sequence.add("stop") },
        )

        lifecycle.handle(CaptureForegroundCommand.START)

        assertEquals(listOf("start"), sequence)
    }

    @Test
    fun stop_command_invokes_session_stop_action() = runTest {
        val sequence = mutableListOf<String>()
        val lifecycle = BleSessionLifecycle(
            onSessionStart = BleSessionAction { sequence.add("start") },
            onSessionStop = BleSessionAction { sequence.add("stop") },
        )

        lifecycle.handle(CaptureForegroundCommand.STOP)

        assertEquals(listOf("stop"), sequence)
    }

    @Test
    fun unknown_command_invokes_neither_action() = runTest {
        val sequence = mutableListOf<String>()
        val lifecycle = BleSessionLifecycle(
            onSessionStart = BleSessionAction { sequence.add("start") },
            onSessionStop = BleSessionAction { sequence.add("stop") },
        )

        lifecycle.handle(CaptureForegroundCommand.UNKNOWN)

        assertEquals(emptyList<String>(), sequence)
    }

    @Test
    fun start_then_stop_runs_both_in_order() = runTest {
        val sequence = mutableListOf<String>()
        val lifecycle = BleSessionLifecycle(
            onSessionStart = BleSessionAction { sequence.add("start") },
            onSessionStop = BleSessionAction { sequence.add("stop") },
        )

        lifecycle.handle(CaptureForegroundCommand.START)
        lifecycle.handle(CaptureForegroundCommand.STOP)

        assertEquals(listOf("start", "stop"), sequence)
    }

    @Test
    fun repeated_start_invokes_action_each_time_so_a_redelivered_intent_is_safe() = runTest {
        // Android FGS START_STICKY redelivers the intent if the process was reaped;
        // the action must be safe to re-fire (idempotency is the action's concern,
        // not the dispatcher's — so the dispatcher invokes every time).
        var startCalls = 0
        val lifecycle = BleSessionLifecycle(
            onSessionStart = BleSessionAction { startCalls++ },
            onSessionStop = BleSessionAction { },
        )

        lifecycle.handle(CaptureForegroundCommand.START)
        lifecycle.handle(CaptureForegroundCommand.START)

        assertEquals(2, startCalls)
    }
}
