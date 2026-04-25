package com.lap.mobile.capture

internal const val THERMAL_STATUS_MODERATE: Int = 2

/**
 * Preflight thermal-state check (T095). Soft-warn (override allowed
 * at the T097 UI layer) when the device is at SEVERE (3) or worse —
 * MODERATE (2) and below pass cleanly.
 *
 * Mirrors the boundary used by [ThermalAwareCapturePolicy] (which
 * pauses motion at SEVERE), so the user never sees a discrepancy
 * between "preflight thinks I'm fine" and "the IMU just stopped".
 *
 * Negative unknown-sentinel readings pass — same defensive logic as
 * [ThermalAwareCapturePolicy] / [BatteryLevelCheck]: a glitchy
 * `PowerManager` shouldn't silently block recording.
 */
class ThermalStateCheck(
    private val reader: ThermalStatusReader,
    private val maxAllowedStatus: Int = THERMAL_STATUS_MODERATE,
) : PreflightCheck {
    override suspend fun failureReason(): String? {
        val status = reader.currentStatus()
        if (status < 0) return null
        return if (status <= maxAllowedStatus) null else REASON_THERMAL_HIGH
    }

    companion object {
        const val REASON_THERMAL_HIGH: String = "thermal_high"
    }
}
