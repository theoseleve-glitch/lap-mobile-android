package com.lap.mobile.dashboard

import com.lap.mobile.capture.ble.BleConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the contract for [RaceboxLinkQualityClassifier] (T200) —
 * the pure function that collapses [BleConnectionState]'s
 * lifecycle 5-state machine into the dashboard's 3-band
 * green/yellow/red signal.
 *
 * Why three bands (not five): the dashboard is a glance surface.
 * A driver mid-corner doesn't need to know whether the link is
 * SCANNING vs CONNECTING vs LOST — those are operational states
 * for the reconnect machinery (T102, T119), not signals the
 * driver acts on. The actionable distinction collapses to:
 *   - "Data flowing — trust the live readouts"     → Connected
 *   - "Working on it — wait or check the device"   → Pending
 *   - "No link, nothing flowing — fix your setup"  → Disconnected
 *
 * Mapping (locked here):
 *   - CONNECTED  → Connected   (green pill)
 *   - SCANNING   → Pending     (yellow — actively probing)
 *   - CONNECTING → Pending     (yellow — handshake in flight)
 *   - LOST       → Pending     (yellow — T119 reconnect running)
 *   - DISCONNECTED → Disconnected (red — terminal idle, no
 *                  reconnect in flight, action required)
 *
 * Why LOST → Pending (not Disconnected): LOST is "we WERE
 * connected, link dropped, T119 is actively trying to recover".
 * The driver's right action is "wait briefly", not "go fix the
 * device". DISCONNECTED is "no recovery in progress" — the
 * action is "tap connect / check power / move closer".
 *
 * Pure function — no Android, no time, no I/O — so the contract
 * is locked by JVM tests and the Android shell can't accidentally
 * change semantics.
 */
class RaceboxLinkQualityClassifierTest {
    @Test
    fun connected_state_maps_to_connected_band_so_the_only_green_state_is_packets_actually_flowing() {
        // Headline T200 invariant: green means trust the data.
        // Anything less must NOT show green.
        assertEquals(
            RaceboxLinkQuality.Connected,
            RaceboxLinkQualityClassifier.classify(BleConnectionState.CONNECTED),
        )
    }

    @Test
    fun scanning_state_maps_to_pending_band_so_an_in_flight_probe_doesnt_falsely_show_disconnected() {
        // Scanning means we're actively looking for the device.
        // Showing red would prompt the driver to "fix" something
        // that's already in progress.
        assertEquals(
            RaceboxLinkQuality.Pending,
            RaceboxLinkQualityClassifier.classify(BleConnectionState.SCANNING),
        )
    }

    @Test
    fun connecting_state_maps_to_pending_band_so_handshake_in_flight_shows_yellow_not_red() {
        // Same rationale as SCANNING — connect handshake is mid-
        // flight, the right action is wait.
        assertEquals(
            RaceboxLinkQuality.Pending,
            RaceboxLinkQualityClassifier.classify(BleConnectionState.CONNECTING),
        )
    }

    @Test
    fun lost_state_maps_to_pending_because_T119_reconnect_is_running_so_yellow_not_red_signals_wait_briefly() {
        // LOST is "we WERE connected, link dropped, T119's
        // reconnect attempts are running from this state." Driver's
        // right action is wait briefly — not "go fix the device".
        // Showing red would trigger needless intervention during a
        // routine micro-disconnect (e.g., a brief antenna shadow).
        assertEquals(
            RaceboxLinkQuality.Pending,
            RaceboxLinkQualityClassifier.classify(BleConnectionState.LOST),
        )
    }

    @Test
    fun disconnected_state_maps_to_disconnected_band_so_terminal_idle_clearly_signals_action_required() {
        // DISCONNECTED is "no scan or connection attempt in flight".
        // The dashboard tells the driver "you need to do something"
        // — tap connect, check device power, move closer. Red is
        // the right shout.
        assertEquals(
            RaceboxLinkQuality.Disconnected,
            RaceboxLinkQualityClassifier.classify(BleConnectionState.DISCONNECTED),
        )
    }
}
