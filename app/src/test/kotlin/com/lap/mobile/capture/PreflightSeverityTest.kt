package com.lap.mobile.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PreflightSeverity] + [PreflightStatus.canStart]
 * (T097). The Start button consults `canStart()` directly. Hard
 * blocks must disable Start; soft warnings (battery, thermal) leave
 * Start enabled — the user has acknowledged the trade-off implicitly
 * by tapping Start.
 *
 * The hard / soft split lives at the UI policy boundary (this file)
 * rather than at each PreflightCheck so a single edit re-classifies
 * a reason — adding a new check defaults to hard-block, which is the
 * safe direction.
 */
class PreflightSeverityTest {
    @Test
    fun ready_status_can_start() {
        assertTrue(PreflightStatus.Ready.canStart())
    }

    @Test
    fun not_ready_with_only_soft_warns_can_start() {
        val status = PreflightStatus.NotReady(
            reasons = listOf(
                BatteryLevelCheck.REASON_BATTERY_LOW,
                ThermalStateCheck.REASON_THERMAL_HIGH,
            ),
        )

        assertTrue("battery + thermal are soft warns — Start enabled", status.canStart())
    }

    @Test
    fun not_ready_with_any_hard_block_cannot_start() {
        val status = PreflightStatus.NotReady(
            reasons = listOf(LocationPermissionCheck.REASON_LOCATION_PERMISSION),
        )

        assertFalse(status.canStart())
    }

    @Test
    fun not_ready_with_mix_of_hard_and_soft_cannot_start() {
        val status = PreflightStatus.NotReady(
            reasons = listOf(
                BatteryLevelCheck.REASON_BATTERY_LOW,
                GpsFixQualityCheck.REASON_GPS_NO_FIX,
                ThermalStateCheck.REASON_THERMAL_HIGH,
            ),
        )

        assertFalse("any hard block disables Start regardless of soft warns", status.canStart())
    }

    @Test
    fun all_block_e_hard_reasons_are_classified_hard() {
        val hardReasons = listOf(
            LocationPermissionCheck.REASON_LOCATION_PERMISSION,
            LocationPermissionCheck.REASON_BACKGROUND_LOCATION_PERMISSION,
            NotificationPermissionCheck.REASON_NOTIFICATION_PERMISSION,
            BluetoothCheck.REASON_BLUETOOTH_PERMISSION,
            BluetoothCheck.REASON_BLUETOOTH_DISABLED,
            RaceboxConnectionCheck.REASON_RACEBOX_NOT_CONNECTED,
            TrackSelectionCheck.REASON_TRACK_NOT_SELECTED,
            GpsFixQualityCheck.REASON_GPS_NO_FIX,
            GpsFixQualityCheck.REASON_GPS_ACCURACY,
            GpsFixQualityCheck.REASON_GPS_SATELLITES,
            StorageAvailabilityCheck.REASON_STORAGE_LOW,
        )

        for (reason in hardReasons) {
            assertTrue("$reason must be hard-block", PreflightSeverity.isHardBlock(reason))
        }
    }

    @Test
    fun all_block_e_soft_reasons_are_classified_soft() {
        val softReasons = listOf(
            BatteryLevelCheck.REASON_BATTERY_LOW,
            ThermalStateCheck.REASON_THERMAL_HIGH,
        )

        for (reason in softReasons) {
            assertFalse("$reason must be soft-warn", PreflightSeverity.isHardBlock(reason))
        }
    }

    @Test
    fun unknown_reason_defaults_to_hard_block() {
        // Adding a future check must default to hard-block — safest direction.
        assertTrue(PreflightSeverity.isHardBlock("future_unknown_reason"))
    }

    @Test
    fun not_ready_with_empty_reason_list_can_start() {
        // Defensive: an empty NotReady (oddball construction) shouldn't block.
        assertTrue(PreflightStatus.NotReady(reasons = emptyList()).canStart())
    }
}
