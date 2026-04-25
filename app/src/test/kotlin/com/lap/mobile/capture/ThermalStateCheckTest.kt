package com.lap.mobile.capture

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val THERMAL_NONE: Int = 0
private const val THERMAL_LIGHT: Int = 1
private const val THERMAL_MODERATE: Int = 2
private const val THERMAL_SEVERE: Int = 3
private const val THERMAL_CRITICAL: Int = 4
private const val THERMAL_EMERGENCY: Int = 5
private const val THERMAL_UNKNOWN_SENTINEL: Int = -1

/**
 * Unit tests for [ThermalStateCheck] (T095). Soft-warn (override
 * allowed at the T097 UI layer) when the device is at SEVERE (3)
 * or worse; MODERATE (2) and below pass.
 *
 * Same defensive behavior as [ThermalAwareCapturePolicy] — negative
 * unknown-sentinel readings pass, never block on OS-side ignorance.
 */
class ThermalStateCheckTest {
    @Test
    fun none_returns_null() = runTest {
        val reader = ThermalStatusReader { THERMAL_NONE }
        val check = ThermalStateCheck(reader)

        assertNull(check.failureReason())
    }

    @Test
    fun light_returns_null() = runTest {
        val reader = ThermalStatusReader { THERMAL_LIGHT }
        val check = ThermalStateCheck(reader)

        assertNull(check.failureReason())
    }

    @Test
    fun moderate_at_boundary_returns_null() = runTest {
        val reader = ThermalStatusReader { THERMAL_MODERATE }
        val check = ThermalStateCheck(reader)

        assertNull("MODERATE (2) is the upper bound — must pass", check.failureReason())
    }

    @Test
    fun severe_returns_thermal_high_reason() = runTest {
        val reader = ThermalStatusReader { THERMAL_SEVERE }
        val check = ThermalStateCheck(reader)

        assertEquals(ThermalStateCheck.REASON_THERMAL_HIGH, check.failureReason())
    }

    @Test
    fun critical_returns_thermal_high_reason() = runTest {
        val reader = ThermalStatusReader { THERMAL_CRITICAL }
        val check = ThermalStateCheck(reader)

        assertEquals(ThermalStateCheck.REASON_THERMAL_HIGH, check.failureReason())
    }

    @Test
    fun emergency_returns_thermal_high_reason() = runTest {
        val reader = ThermalStatusReader { THERMAL_EMERGENCY }
        val check = ThermalStateCheck(reader)

        assertEquals(ThermalStateCheck.REASON_THERMAL_HIGH, check.failureReason())
    }

    @Test
    fun unknown_sentinel_returns_null() = runTest {
        val reader = ThermalStatusReader { THERMAL_UNKNOWN_SENTINEL }
        val check = ThermalStateCheck(reader)

        assertNull("negative unknown-sentinel passes — never block on OS-side ignorance", check.failureReason())
    }
}
