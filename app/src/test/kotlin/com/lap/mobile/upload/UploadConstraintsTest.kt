package com.lap.mobile.upload

import androidx.work.NetworkType
import com.lap.mobile.data.NetworkPolicy
import org.junit.Assert.assertEquals
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
}
