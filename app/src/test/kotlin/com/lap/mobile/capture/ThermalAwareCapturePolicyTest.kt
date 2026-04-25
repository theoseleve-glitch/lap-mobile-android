package com.lap.mobile.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val THERMAL_NONE: Int = 0
private const val THERMAL_LIGHT: Int = 1
private const val THERMAL_MODERATE: Int = 2
private const val THERMAL_SEVERE: Int = 3
private const val THERMAL_CRITICAL: Int = 4
private const val THERMAL_EMERGENCY: Int = 5
private const val THERMAL_SHUTDOWN: Int = 6
private const val THERMAL_UNKNOWN_NEGATIVE: Int = -1

/**
 * Thermal-aware sensor pausing (T074). Constants mirror
 * [android.os.PowerManager.THERMAL_STATUS_NONE] … `THERMAL_STATUS_SHUTDOWN`
 * — defined as literals here so JVM tests don't drag in the Android
 * runtime, same pattern as [CaptureNotification] importance.
 *
 * Contract: motion (accelerometer + gyroscope) is "non-critical" and
 * gets paused at SEVERE or worse. Location stays untouched at every
 * thermal level — GPS is irreplaceable for lap detection, and the
 * preflight gate (T091) already soft-warns at MODERATE.
 *
 * Spec uses "= SEVERE" but treating SEVERE as a *threshold* (≥) is
 * what the user actually wants — CRITICAL/EMERGENCY/SHUTDOWN must
 * also pause.
 */
class ThermalAwareCapturePolicyTest {
    private val policy = ThermalAwareCapturePolicy()

    @Test
    fun motion_enabled_at_thermal_none() {
        assertTrue(policy.motionEnabled(THERMAL_NONE))
    }

    @Test
    fun motion_enabled_at_thermal_light() {
        assertTrue(policy.motionEnabled(THERMAL_LIGHT))
    }

    @Test
    fun motion_enabled_at_thermal_moderate() {
        // T091 soft-warns at MODERATE; T074 still keeps motion on. Pause is SEVERE+.
        assertTrue(policy.motionEnabled(THERMAL_MODERATE))
    }

    @Test
    fun motion_paused_at_thermal_severe() {
        assertFalse(policy.motionEnabled(THERMAL_SEVERE))
    }

    @Test
    fun motion_paused_at_thermal_critical() {
        assertFalse(policy.motionEnabled(THERMAL_CRITICAL))
    }

    @Test
    fun motion_paused_at_thermal_emergency() {
        assertFalse(policy.motionEnabled(THERMAL_EMERGENCY))
    }

    @Test
    fun motion_paused_at_thermal_shutdown() {
        assertFalse(policy.motionEnabled(THERMAL_SHUTDOWN))
    }

    @Test
    fun motion_enabled_when_thermal_status_is_unknown() {
        // Defensive default — same logic as BatteryAwareMotionPolicy. A glitchy
        // PowerManager shouldn't silently disable the IMU mid-session.
        assertTrue(policy.motionEnabled(THERMAL_UNKNOWN_NEGATIVE))
    }

    @Test
    fun policy_exposes_only_motion_decision() {
        // Location is "critical" and stays on at every thermal level by virtue
        // of this policy not exposing a way to pause it. The day someone adds a
        // `locationEnabled` tier, this test forces the conversation.
        val methodNames = ThermalAwareCapturePolicy::class.java.declaredMethods
            .map { it.name }
            .toSet()
        assertTrue(
            "policy must expose motionEnabled; got $methodNames",
            "motionEnabled" in methodNames,
        )
        assertFalse(
            "policy must NOT expose locationEnabled — pause-on-thermal applies only to motion",
            "locationEnabled" in methodNames,
        )
    }
}
