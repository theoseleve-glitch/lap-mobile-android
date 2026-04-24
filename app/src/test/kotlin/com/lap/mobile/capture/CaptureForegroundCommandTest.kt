package com.lap.mobile.capture

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Action-string ↔ command mapping for [CaptureForegroundService].
 * The service itself is a Service subclass that needs an Android
 * runtime to exercise; the dispatch logic is hoisted into this enum
 * so the start/stop contract has a JVM-testable home.
 */
class CaptureForegroundCommandTest {
    @Test
    fun start_action_maps_to_start() {
        assertEquals(
            CaptureForegroundCommand.START,
            CaptureForegroundCommand.from(CaptureForegroundCommand.ACTION_START),
        )
    }

    @Test
    fun stop_action_maps_to_stop() {
        assertEquals(
            CaptureForegroundCommand.STOP,
            CaptureForegroundCommand.from(CaptureForegroundCommand.ACTION_STOP),
        )
    }

    @Test
    fun null_action_maps_to_unknown() {
        // System-restart deliveries (START_STICKY redelivery on null intent)
        // hit this branch — the service must not start a fresh recording
        // session in response.
        assertEquals(CaptureForegroundCommand.UNKNOWN, CaptureForegroundCommand.from(null))
    }

    @Test
    fun unrecognized_action_maps_to_unknown() {
        assertEquals(
            CaptureForegroundCommand.UNKNOWN,
            CaptureForegroundCommand.from("com.example.bogus"),
        )
    }

    @Test
    fun action_strings_are_namespaced_to_app() {
        // Implicit-broadcast leakage protection — both actions must include
        // the app package so other apps can't trigger them by accident.
        assertEquals(true, CaptureForegroundCommand.ACTION_START.startsWith("com.lap.mobile."))
        assertEquals(true, CaptureForegroundCommand.ACTION_STOP.startsWith("com.lap.mobile."))
    }
}
