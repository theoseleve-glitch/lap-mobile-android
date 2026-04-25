package com.lap.mobile.capture

/**
 * `THERMAL_STATUS_SEVERE` per [android.os.PowerManager.THERMAL_STATUS_SEVERE].
 * Defined as a literal so the JVM-pure policy doesn't drag in the
 * Android runtime — same constants-as-numbers convention as
 * [CaptureNotification.IMPORTANCE].
 */
private const val PAUSE_THRESHOLD_THERMAL_STATUS: Int = 3

private const val UNKNOWN_THERMAL_BOUNDARY: Int = 0

/**
 * Pure decision policy for "should the IMU stream still run at this
 * thermal level?" (T074). Motion is non-critical and pauses at SEVERE
 * or worse so the device gets a chance to cool down without losing
 * the only sensor that actually matters for lap detection (location,
 * which is never paused by this policy).
 *
 * The threshold is `>=` SEVERE — task spec says "= SEVERE" but
 * CRITICAL/EMERGENCY/SHUTDOWN are strictly worse and must also pause.
 *
 * Negative thermal-status readings (a hypothetical "unknown" sentinel
 * from a future Android API change) default to motion-enabled — same
 * defensive logic as [BatteryAwareMotionPolicy]. A glitchy
 * PowerManager shouldn't silently disable the IMU mid-session.
 *
 * The policy itself doesn't observe `PowerManager` — the consumer
 * (T076 SessionCoordinator wiring) registers a
 * `OnThermalStatusChangedListener` and re-invokes the methods on each
 * tick.
 */
class ThermalAwareCapturePolicy {
    fun motionEnabled(thermalStatus: Int): Boolean = when {
        thermalStatus < UNKNOWN_THERMAL_BOUNDARY -> true
        thermalStatus < PAUSE_THRESHOLD_THERMAL_STATUS -> true
        else -> false
    }

    // No locationEnabled() — location is critical and stays on at every thermal
    // level by virtue of this policy not exposing a way to pause it. T091's
    // preflight gate is where genuinely-broken-GPS surfaces.
}
