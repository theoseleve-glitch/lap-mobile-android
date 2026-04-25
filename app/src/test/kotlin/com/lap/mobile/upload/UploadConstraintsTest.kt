package com.lap.mobile.upload

import androidx.work.NetworkType
import com.lap.mobile.data.BatteryPolicy
import com.lap.mobile.data.NetworkPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadConstraintsTest {
    @Test
    fun unmetered_only_policy_requires_unmetered_network_type() {
        // The Settings WiFi-only toggle ON (T157 default; T156 persists
        // it as allow_metered=false → UNMETERED_ONLY) MUST translate to
        // WorkManager's UNMETERED constraint so the worker waits for
        // Wi-Fi rather than running over cellular.
        val constraints = NetworkPolicy.UNMETERED_ONLY.toUploadConstraints()

        assertEquals(NetworkType.UNMETERED, constraints.requiredNetworkType)
    }

    @Test
    fun allow_metered_policy_only_requires_a_connected_network() {
        // Toggle OFF (allow_metered=true) lets the worker run on any
        // connection — Wi-Fi, cellular, ethernet — as long as something
        // is actually online.
        val constraints = NetworkPolicy.ALLOW_METERED.toUploadConstraints()

        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
    }

    @Test
    fun defer_when_low_battery_policy_sets_requires_battery_not_low_so_worker_waits_for_recovery() {
        // T158 default. A multi-hour bundle on a near-empty device would
        // strand the user — defer until battery recovers.
        val constraints = NetworkPolicy.UNMETERED_ONLY
            .toUploadConstraints(batteryPolicy = BatteryPolicy.DEFER_WHEN_LOW)

        assertTrue(
            "DEFER_WHEN_LOW must set requiresBatteryNotLow=true so WorkManager parks the upload",
            constraints.requiresBatteryNotLow(),
        )
    }

    @Test
    fun force_regardless_battery_policy_clears_requires_battery_not_low_so_user_force_overrides_the_gate() {
        // User picked "force this upload now even if battery is low" —
        // typical when they're plugged into a tender at the track and want
        // last session uploaded before disconnecting.
        val constraints = NetworkPolicy.UNMETERED_ONLY
            .toUploadConstraints(batteryPolicy = BatteryPolicy.FORCE_REGARDLESS)

        assertFalse(
            "FORCE_REGARDLESS must set requiresBatteryNotLow=false so the gate is open",
            constraints.requiresBatteryNotLow(),
        )
    }

    @Test
    fun default_battery_policy_is_defer_when_low_so_callers_get_the_safe_path_without_thinking() {
        // Lock the parameter default so a future change doesn't silently
        // flip the worker into "drain the battery on every upload" mode.
        val constraints = NetworkPolicy.UNMETERED_ONLY.toUploadConstraints()

        assertTrue(
            "default toUploadConstraints() must require battery-not-low",
            constraints.requiresBatteryNotLow(),
        )
    }
}
