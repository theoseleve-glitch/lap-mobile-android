package com.lap.mobile.capture

/**
 * Preflight check that the RaceBox BLE peripheral is reachable
 * (T092). Skips when the user hasn't opted into RaceBox mode —
 * iRacing-only or pure-mobile-GNSS sessions don't ride this rail.
 *
 * In RaceBox mode the live GATT connection is the *primary* GNSS
 * source (the device's external receiver is what makes RaceBox
 * worth using); without it the session would silently fall back to
 * phone GNSS, which defeats the user's hardware investment. Hence
 * a hard block rather than a warn.
 */
class RaceboxConnectionCheck(
    private val status: RaceboxConnectionStatus,
    private val raceboxModeEnabled: () -> Boolean,
) : PreflightCheck {
    override suspend fun failureReason(): String? {
        if (!raceboxModeEnabled()) return null
        return if (status.isConnected()) null else REASON_RACEBOX_NOT_CONNECTED
    }

    companion object {
        const val REASON_RACEBOX_NOT_CONNECTED: String = "racebox_not_connected"
    }
}
