package com.lap.mobile.capture

/**
 * Dispatch contract for [CaptureForegroundService]'s `onStartCommand`.
 * Hoisting the action→command mapping out of the Service makes the
 * start/stop wiring testable on the JVM (the Service itself is an
 * Android subclass that needs Robolectric or a device to exercise).
 *
 * Action strings are namespaced under `com.lap.mobile.` so they don't
 * collide with other apps' implicit broadcasts.
 */
enum class CaptureForegroundCommand {
    START,
    STOP,
    UNKNOWN,
    ;

    companion object {
        const val ACTION_START: String = "com.lap.mobile.capture.action.START"
        const val ACTION_STOP: String = "com.lap.mobile.capture.action.STOP"

        fun from(action: String?): CaptureForegroundCommand = when (action) {
            ACTION_START -> START
            ACTION_STOP -> STOP
            else -> UNKNOWN
        }
    }
}
