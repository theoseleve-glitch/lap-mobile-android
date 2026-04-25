package com.lap.mobile.capture

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val FULL_BATTERY: Int = 100
private const val MID_BATTERY: Int = 50
private const val EXACT_THRESHOLD_BATTERY: Int = 15
private const val LOW_BATTERY: Int = 14
private const val EMPTY_BATTERY: Int = 0
private const val UNKNOWN_BATTERY: Int = -1

/**
 * Unit tests for [BatteryLevelCheck] (T094). The task spec calls
 * this a *soft* warning — the override is enacted at the UI layer
 * (T097) by classifying [REASON_BATTERY_LOW] as overridable. From
 * this check's perspective the policy is the same as any other
 * [PreflightCheck]: surface the reason if the threshold is missed.
 *
 * Unknown-battery sentinel (negative) MUST pass — better to allow
 * recording on a flaky read than to block the user when the OS
 * itself doesn't know.
 */
class BatteryLevelCheckTest {
    @Test
    fun full_battery_returns_null() = runTest {
        val battery = BatteryStatus { FULL_BATTERY }
        val check = BatteryLevelCheck(battery)

        assertNull(check.failureReason())
    }

    @Test
    fun mid_battery_returns_null() = runTest {
        val battery = BatteryStatus { MID_BATTERY }
        val check = BatteryLevelCheck(battery)

        assertNull(check.failureReason())
    }

    @Test
    fun exactly_at_threshold_returns_null() = runTest {
        val battery = BatteryStatus { EXACT_THRESHOLD_BATTERY }
        val check = BatteryLevelCheck(battery)

        assertNull("≥ 15% is the boundary — exactly 15 must pass", check.failureReason())
    }

    @Test
    fun below_threshold_returns_battery_low_reason() = runTest {
        val battery = BatteryStatus { LOW_BATTERY }
        val check = BatteryLevelCheck(battery)

        assertEquals(BatteryLevelCheck.REASON_BATTERY_LOW, check.failureReason())
    }

    @Test
    fun empty_battery_returns_battery_low_reason() = runTest {
        val battery = BatteryStatus { EMPTY_BATTERY }
        val check = BatteryLevelCheck(battery)

        assertEquals(BatteryLevelCheck.REASON_BATTERY_LOW, check.failureReason())
    }

    @Test
    fun unknown_battery_sentinel_returns_null() = runTest {
        val battery = BatteryStatus { UNKNOWN_BATTERY }
        val check = BatteryLevelCheck(battery)

        assertNull("unknown battery (-1) passes — never block on OS-side ignorance", check.failureReason())
    }
}
