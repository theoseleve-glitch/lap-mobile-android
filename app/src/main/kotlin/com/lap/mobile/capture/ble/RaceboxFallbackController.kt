package com.lap.mobile.capture.ble

/**
 * Sink for session-journal events about RaceBox availability gaps
 * (T120). Per ADR-012 the bundle's session-level journal carries
 * gap markers so the server can reason about why a stretch of
 * samples switched source clock — without it the GNSS series looks
 * like a discontinuity from `racebox_gnss` to `gnss` with no cause.
 *
 * Single-method fun-interface: the only event the fallback path
 * raises is "RaceBox link lost mid-session". A future
 * "RaceBox restored" hook would be a sibling fun-interface so each
 * remains independently testable.
 */
fun interface SessionGapJournal {
    fun recordRaceboxDisconnect(atNs: Long)
}

/**
 * Toggle for swapping the active GNSS producer over to the phone's
 * built-in receiver while RaceBox is unavailable (T120). Kept as a
 * single-method seam — the platform impl wires this to the capture
 * orchestrator's source selector; the policy itself doesn't need
 * to know which scheduler/flow to flip.
 */
fun interface FallbackSourceSwitch {
    fun usePhoneGps()
}

/**
 * Mid-session RaceBox-loss handler (T120). Invoked once the
 * reconnect policy (T119) has exhausted its three attempts and the
 * link is confirmed gone. Two side effects, in order:
 *
 *  1. Journal the disconnect timestamp so the server knows the
 *     source-clock change wasn't a config swap.
 *  2. Switch the active GNSS source to the phone's built-in
 *     receiver so the laptime/sector pipeline keeps producing
 *     samples (degraded accuracy, but better than no fix).
 *
 * Order matters: journal first, switch second. If the switch
 * threw or the process died between the two, we'd still have a
 * durable gap marker server-side — recoverable. The opposite order
 * would risk a silent cutover with no record.
 */
class RaceboxFallbackController(
    private val journal: SessionGapJournal,
    private val sourceSwitch: FallbackSourceSwitch,
) {
    fun handleFinalDisconnect(atNs: Long) {
        journal.recordRaceboxDisconnect(atNs)
        sourceSwitch.usePhoneGps()
    }
}
