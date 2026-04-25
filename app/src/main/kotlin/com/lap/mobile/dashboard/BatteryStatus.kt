package com.lap.mobile.dashboard

/**
 * Battery-warning band for the dashboard's
 * [com.lap.mobile.ui.dashboard.BatteryIndicator] (T201).
 *
 * Two states only — drivers care about the binary "do I need to
 * think about charging soon?":
 *   - [Normal]:     ≥20% — no warning, neutral display.
 *   - [LowWarning]: <20% — warning visual (errorContainer pill).
 *
 * No yellow-intermediate "Low" band in v1 — it would over-warn
 * and dilute the signal. If a future need splits this (e.g.,
 * 5% "stop now" critical band), the classifier grows a state.
 */
enum class BatteryStatus {
    Normal,
    LowWarning,
}

/**
 * Pure-function classifier: battery percentage → [BatteryStatus]
 * (T201 kernel).
 *
 * Threshold: strict-less-than 20%. A reading of exactly 20% is
 * Normal — drivers say "I crossed BELOW 20" to describe engagement,
 * so the boundary belongs to the safer band.
 *
 * Defensive cases:
 *   - 0% or negative percentages collapse to [BatteryStatus.LowWarning]
 *     (clearly in the danger zone or sensor reporting nonsense —
 *     surfacing as warning is the safer driver-facing default).
 *   - >100% (sensor over-report glitch) treated as Normal —
 *     over-reporting is annoying, not dangerous.
 *
 * Pure function — no Android, no time, no I/O — so the contract
 * is locked by JVM tests and the Android shell can't accidentally
 * change semantics by changing how it observes BatteryManager.
 */
object BatteryStatusClassifier {
    fun classify(percentage: Int): BatteryStatus =
        if (percentage < WARNING_THRESHOLD) BatteryStatus.LowWarning else BatteryStatus.Normal

    private const val WARNING_THRESHOLD: Int = 20
}
