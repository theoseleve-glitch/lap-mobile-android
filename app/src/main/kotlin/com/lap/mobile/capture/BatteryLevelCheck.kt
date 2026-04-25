package com.lap.mobile.capture

internal const val DEFAULT_MIN_BATTERY_PERCENT: Int = 15

/**
 * Preflight battery-level check (T094). Surfaces
 * [REASON_BATTERY_LOW] when the device reports < 15% — the task
 * spec calls this a *soft* warning, with override allowed; that
 * affordance lives at the UI layer (T097), which classifies which
 * reasons are overridable. From the check's perspective the policy
 * is identical to any [PreflightCheck]: report the failure reason
 * if the threshold is missed.
 *
 * Unknown-battery sentinel (negative percent — `BatteryManager`
 * returns this on transient capacity-read failures) is treated as
 * a pass, not a failure: never block recording because the OS
 * itself doesn't know.
 *
 * Boundary semantics: `>= minBatteryPercent` passes, so exactly
 * 15% is acceptable.
 */
class BatteryLevelCheck(
    private val battery: BatteryStatus,
    private val minBatteryPercent: Int = DEFAULT_MIN_BATTERY_PERCENT,
) : PreflightCheck {
    override suspend fun failureReason(): String? {
        val pct = battery.levelPercent()
        if (pct < 0) return null
        return if (pct >= minBatteryPercent) null else REASON_BATTERY_LOW
    }

    companion object {
        const val REASON_BATTERY_LOW: String = "battery_low"
    }
}
