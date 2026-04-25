package com.lap.mobile.capture

/**
 * UI-policy boundary that classifies preflight failure reasons into
 * hard blocks (Start button disabled) vs soft warnings (Start
 * enabled, banner shown) — the affordance T097 calls "override".
 *
 * Centralising this list (rather than tagging each [PreflightCheck])
 * means re-classifying a reason is a single edit, and adding a new
 * check defaults to hard-block which is the safe direction (a
 * forgotten registration won't accidentally enable Start with a
 * silently-failing check).
 */
object PreflightSeverity {
    private val SOFT_WARN_REASONS: Set<String> = setOf(
        BatteryLevelCheck.REASON_BATTERY_LOW,
        ThermalStateCheck.REASON_THERMAL_HIGH,
    )

    fun isHardBlock(reason: String): Boolean = reason !in SOFT_WARN_REASONS
}

/**
 * Convenience for the Start button (T097): true iff every blocker
 * (if any) is a soft warning the user can override by tapping Start.
 */
fun PreflightStatus.canStart(): Boolean = when (this) {
    is PreflightStatus.Ready -> true
    is PreflightStatus.NotReady -> reasons.none { PreflightSeverity.isHardBlock(it) }
}
