package com.lap.mobile.upload

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectivityKickPolicyTest {
    @Test
    fun first_observation_with_network_already_available_kicks_so_app_launch_clears_pending_backlog() {
        // App restored after offline stretch → we land into onAvailable
        // immediately at observer-registration time. Kicking here drains
        // whatever the queue accumulated while we were dead/offline.
        val shouldKick = ConnectivityKickPolicy.shouldKickQueue(
            previous = null,
            current = NetworkAvailability.Available,
        )

        assertTrue(shouldKick)
    }

    @Test
    fun first_observation_with_no_network_does_not_kick_because_worker_would_just_block_on_constraint() {
        val shouldKick = ConnectivityKickPolicy.shouldKickQueue(
            previous = null,
            current = NetworkAvailability.Unavailable,
        )

        assertFalse(shouldKick)
    }

    @Test
    fun transitioning_from_unavailable_to_available_kicks_so_queue_resumes_immediately_on_reconnect() {
        // Headline behavior: airplane-mode-off, train-leaves-tunnel, etc.
        // Without this, WorkManager's NetworkType constraint relaxes only
        // when WorkManager next polls — seconds-to-minutes lag the user
        // would notice as "uploads stuck after I got wifi back."
        val shouldKick = ConnectivityKickPolicy.shouldKickQueue(
            previous = NetworkAvailability.Unavailable,
            current = NetworkAvailability.Available,
        )

        assertTrue(shouldKick)
    }

    @Test
    fun transitioning_from_available_to_unavailable_does_not_kick_because_no_network_to_use() {
        val shouldKick = ConnectivityKickPolicy.shouldKickQueue(
            previous = NetworkAvailability.Available,
            current = NetworkAvailability.Unavailable,
        )

        assertFalse(shouldKick)
    }

    @Test
    fun repeated_available_observations_do_not_kick_again_so_we_dont_thrash_on_wifi_band_handoffs() {
        // ConnectivityManager fires onAvailable per-Network — wifi-2.4 to
        // wifi-5 handoff yields two onAvailable callbacks. The worker is
        // already running (or already enqueued via T159 KEEP); a second
        // kick would pointlessly hammer the workmanager queue.
        val shouldKick = ConnectivityKickPolicy.shouldKickQueue(
            previous = NetworkAvailability.Available,
            current = NetworkAvailability.Available,
        )

        assertFalse(shouldKick)
    }

    @Test
    fun repeated_unavailable_observations_do_not_kick_so_we_dont_wake_for_no_progress() {
        val shouldKick = ConnectivityKickPolicy.shouldKickQueue(
            previous = NetworkAvailability.Unavailable,
            current = NetworkAvailability.Unavailable,
        )

        assertFalse(shouldKick)
    }
}
