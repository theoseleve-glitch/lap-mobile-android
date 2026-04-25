package com.lap.mobile.capture

/**
 * Gate the [com.lap.mobile.data.SessionCoordinator] consults before
 * the ARMED → RECORDING transition (T077). The Block E preflight
 * service (T086) eventually implements this interface; the small
 * surface here lets T076's coordinator enforce the gate without
 * pulling in the full preflight surface ahead of T086.
 */
fun interface PreflightGate {
    suspend fun check(): PreflightStatus
}
