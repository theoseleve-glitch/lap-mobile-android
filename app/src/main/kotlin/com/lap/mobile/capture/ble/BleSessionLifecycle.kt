package com.lap.mobile.capture.ble

import com.lap.mobile.capture.CaptureForegroundCommand

/**
 * Single suspend action seam (T126). Wraps either "start the BLE
 * session" (scan + connect + begin streaming UBX frames) or "stop
 * it" (cancel collectors + disconnect GATT). Idempotency is the
 * action's concern — Android FGS `START_STICKY` redelivers the
 * intent if the OS reaped the process, so the dispatcher invokes
 * every time it gets a [CaptureForegroundCommand.START].
 */
fun interface BleSessionAction {
    suspend fun invoke()
}

/**
 * Binds the BLE session lifecycle to [com.lap.mobile.capture.CaptureForegroundService]'s
 * dispatch contract (T126). When the FGS receives [CaptureForegroundCommand.START]
 * we open the BLE link; on [CaptureForegroundCommand.STOP] we tear it down.
 *
 * Why bind to the FGS lifecycle rather than the activity's? Android
 * 12+ only keeps a process alive in the background when an FGS with
 * `foregroundServiceType=connectedDevice` is running — without this
 * binding the BLE link tears down whenever the user backgrounds the
 * app, which is exactly the failure mode T126 prevents.
 *
 * The dispatcher is pure / Android-free; the actual FGS hookup lives
 * in [com.lap.mobile.capture.CaptureForegroundService]. Live "survives
 * backgrounding" verification is `[PENDING: device-required]`.
 */
class BleSessionLifecycle(
    private val onSessionStart: BleSessionAction,
    private val onSessionStop: BleSessionAction,
) {
    suspend fun handle(command: CaptureForegroundCommand) {
        when (command) {
            CaptureForegroundCommand.START -> onSessionStart.invoke()
            CaptureForegroundCommand.STOP -> onSessionStop.invoke()
            CaptureForegroundCommand.UNKNOWN -> Unit
        }
    }
}
