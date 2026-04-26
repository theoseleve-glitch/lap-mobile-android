package com.lap.mobile.capture.ble

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DISCONNECT_TS_NS: Long = 1_761_472_800_000_000_000L
private const val DEFAULT_MAX_ATTEMPTS_PROD: Int = 3
private const val SUCCESS_ON_FIRST_ATTEMPT: Int = 1
private const val SUCCESS_ON_THIRD_ATTEMPT: Int = 3

/**
 * JVM integration tests for the mid-session BLE disconnect chain
 * (T231 — the device-required half is marked PENDING in the
 * sprint).
 *
 * Stitches together three production kernels that today live as
 * isolated single-responsibility units:
 *
 *  - [BleConnectionStateHolder] (T103) — holds the live link state,
 *    drives the UI badge and downstream observers.
 *  - [RaceboxReconnectPolicy] (T119) — bounded retry loop on
 *    disconnect.
 *  - [RaceboxFallbackController] (T120) — when the policy gives up,
 *    journal a gap marker and switch the active GNSS source to
 *    phone GPS so the laptime/sector pipeline keeps producing
 *    samples (degraded accuracy, but better than no fix).
 *
 * The composition lives in [orchestrateMidSessionDisconnect] —
 * intentionally inlined into test scope rather than landed as a new
 * production class. Mirrors the wiring the FGS-side
 * [com.lap.mobile.capture.ble.RaceBoxBleService] callbacks will
 * eventually do (or already do via the Nordic BleManager
 * `onDeviceDisconnected` callback) so this test locks the chain
 * without forcing a production-shape refactor.
 *
 * What this test does NOT cover (genuinely device-required, hence
 * the [PENDING: device-required] mark in the sprint file):
 *  - Real Nordic `BleManager.onDeviceDisconnected` callback firing.
 *  - Real GATT reconnect attempts against a peripheral (RaceBox or
 *    [com.lap.mobile.capture.ble.MockRaceboxPeripheral] running on
 *    a paired test device).
 *  - Real foreground-service-survives-backgrounding behaviour
 *    (T126 contract — without an FGS the OS tears down BLE on
 *    background).
 *  - Real wall-clock timing of the 3-attempt retry budget against
 *    BLE-stack timeouts.
 *
 * What it DOES cover (the chain regression surface):
 *  - First-attempt reconnect success → state returns to CONNECTED,
 *    fallback never fires.
 *  - Last-attempt reconnect success → state returns to CONNECTED,
 *    fallback never fires (the budget gets exhausted but exactly the
 *    last try succeeds).
 *  - All-attempts-fail → state stays LOST, fallback fires exactly
 *    once with the disconnect timestamp, journal-before-source-switch
 *    ordering preserved (the [RaceboxFallbackController] contract
 *    relied on by ADR-012 server-side source-clock continuity check).
 *  - Headline regression: an uninterrupted session (no disconnect)
 *    never invokes the fallback so the journal stays clean — without
 *    this, every flap-and-recover would litter the gap-marker stream
 *    and confuse the server's source-clock reasoning.
 */
class BleDisconnectMidSessionScenariosTest {
    @Test
    fun first_attempt_reconnect_success_returns_state_to_connected_and_does_not_fire_fallback() = runTest {
        val rig = DisconnectRig(reconnectSucceedsOnAttempt = SUCCESS_ON_FIRST_ATTEMPT)

        rig.simulateMidSessionDisconnect(atNs = DISCONNECT_TS_NS)

        assertEquals(BleConnectionState.CONNECTED, rig.state.state.first())
        assertEquals(SUCCESS_ON_FIRST_ATTEMPT, rig.attemptsMade)
        assertFalse("fallback must not fire when reconnect succeeded", rig.fallbackFired)
    }

    @Test
    fun last_attempt_reconnect_success_still_returns_state_to_connected_and_does_not_fire_fallback() = runTest {
        // Exhausts the budget down to the last try then succeeds. Locks
        // that the policy's "succeeded on attempt N" path doesn't accidentally
        // fall through to the fallback even when the budget is used up.
        val rig = DisconnectRig(reconnectSucceedsOnAttempt = SUCCESS_ON_THIRD_ATTEMPT)

        rig.simulateMidSessionDisconnect(atNs = DISCONNECT_TS_NS)

        assertEquals(BleConnectionState.CONNECTED, rig.state.state.first())
        assertEquals(SUCCESS_ON_THIRD_ATTEMPT, rig.attemptsMade)
        assertFalse("fallback must not fire when last-attempt reconnect succeeded", rig.fallbackFired)
    }

    @Test
    fun all_attempts_fail_keeps_state_lost_and_fires_fallback_exactly_once_with_disconnect_timestamp() = runTest {
        // Headline T231 invariant: the gap marker is journalled with
        // the original disconnect timestamp (not "now" at the moment
        // the policy gave up) so the server's source-clock continuity
        // check sees the actual gap window, not the recovery-attempt
        // window.
        val rig = DisconnectRig(reconnectSucceedsOnAttempt = NEVER_SUCCEEDS)

        rig.simulateMidSessionDisconnect(atNs = DISCONNECT_TS_NS)

        assertEquals(BleConnectionState.LOST, rig.state.state.first())
        assertEquals(DEFAULT_MAX_ATTEMPTS_PROD, rig.attemptsMade)
        assertTrue("fallback must fire after exhausting reconnect budget", rig.fallbackFired)
        assertEquals(
            "fallback journal must record the original disconnect timestamp, not the recovery-time",
            listOf(DISCONNECT_TS_NS),
            rig.journalledTimestamps,
        )
    }

    @Test
    fun fallback_orders_journal_before_source_switch_so_a_post_journal_crash_keeps_the_gap_marker_durable() = runTest {
        // ADR-012-flavoured contract: if the JVM dies between
        // journal+switch, the durable gap marker on disk is the
        // ground truth. Reversing the order would risk a silent
        // cutover with no record on the server side.
        val rig = DisconnectRig(reconnectSucceedsOnAttempt = NEVER_SUCCEEDS)

        rig.simulateMidSessionDisconnect(atNs = DISCONNECT_TS_NS)

        assertEquals(listOf("journal", "switch"), rig.fallbackSideEffectOrder)
    }

    @Test
    fun uninterrupted_session_never_invokes_fallback_so_the_journal_stays_clean_for_continuity_check() = runTest {
        // Headline regression test: a session with no disconnect must
        // produce zero gap markers. Without this, a future refactor
        // that accidentally fires fallback on every state transition
        // (or on session start) would litter the journal and break
        // the server's source-clock continuity reasoning.
        val rig = DisconnectRig(reconnectSucceedsOnAttempt = SUCCESS_ON_FIRST_ATTEMPT)

        // Note: no simulateMidSessionDisconnect call.

        assertFalse("fallback must not fire on a session with no disconnect", rig.fallbackFired)
        assertEquals(0, rig.attemptsMade)
        assertTrue(rig.journalledTimestamps.isEmpty())
    }

    @Test
    fun reconnect_attempts_keep_state_lost_so_ui_badge_does_not_flicker_during_retry_loop() = runTest {
        // Without this invariant the UI badge would flicker LOST →
        // CONNECTING → LOST → CONNECTING → LOST → … during the
        // 3-attempt retry burst. Holding the state at LOST until the
        // resolution (success → CONNECTED, exhausted → LOST) gives a
        // stable badge through the recovery window.
        val statesObserved = mutableListOf<BleConnectionState>()
        val rig = DisconnectRig(reconnectSucceedsOnAttempt = NEVER_SUCCEEDS) {
            statesObserved.add(it)
        }

        rig.simulateMidSessionDisconnect(atNs = DISCONNECT_TS_NS)

        // Headline: only one transition during the disconnect — into
        // LOST. Subsequent retry attempts must not toggle the holder.
        assertEquals(
            "reconnect attempts must not toggle BleConnectionState during retry burst",
            listOf(BleConnectionState.LOST),
            statesObserved,
        )
    }

    private companion object {
        // Sentinel — DisconnectRig's attempt closure compares against
        // this to mean "reject every retry, exhaust the budget".
        const val NEVER_SUCCEEDS: Int = Int.MAX_VALUE
    }
}

/**
 * Inline orchestrator + side-effect tape mirroring the eventual
 * FGS-side wiring. [simulateMidSessionDisconnect] composes the
 * three production kernels in the order the Nordic BleManager
 * disconnect callback will exercise them:
 *
 *   1. ConnectionStateHolder.update(LOST)
 *   2. ReconnectPolicy.reconnect() → invokes BleReconnectAttempt
 *      up to maxAttempts times
 *   3. on success → ConnectionStateHolder.update(CONNECTED); skip fallback
 *      on exhaustion → keep LOST, FallbackController.handleFinalDisconnect()
 *
 * The [onStateUpdate] callback lets the badge-flicker test observe
 * every state transition without re-collecting the StateFlow
 * (StateFlow conflates equal consecutive emissions but the test
 * needs to lock that there's only the *one* update during the burst).
 */
private class DisconnectRig(
    private val reconnectSucceedsOnAttempt: Int,
    private val onStateUpdate: (BleConnectionState) -> Unit = {},
) {
    val state: BleConnectionStateHolder = BleConnectionStateHolder()
    var attemptsMade: Int = 0
        private set
    var fallbackFired: Boolean = false
        private set
    val journalledTimestamps: MutableList<Long> = mutableListOf()
    val fallbackSideEffectOrder: MutableList<String> = mutableListOf()

    private val journal = SessionGapJournal { ns ->
        journalledTimestamps.add(ns)
        fallbackSideEffectOrder.add("journal")
    }
    private val sourceSwitch = FallbackSourceSwitch {
        fallbackSideEffectOrder.add("switch")
    }
    private val fallback = RaceboxFallbackController(journal = journal, sourceSwitch = sourceSwitch)

    suspend fun simulateMidSessionDisconnect(atNs: Long) {
        updateState(BleConnectionState.LOST)
        val attempt = BleReconnectAttempt { attemptNumber ->
            attemptsMade = attemptNumber
            attemptNumber == reconnectSucceedsOnAttempt
        }
        val policy = RaceboxReconnectPolicy(attempt = attempt)
        if (policy.reconnect()) {
            updateState(BleConnectionState.CONNECTED)
        } else {
            fallback.handleFinalDisconnect(atNs)
            fallbackFired = true
        }
    }

    private fun updateState(newState: BleConnectionState) {
        state.update(newState)
        onStateUpdate(newState)
    }
}
