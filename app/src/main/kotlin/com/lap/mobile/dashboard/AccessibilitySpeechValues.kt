package com.lap.mobile.dashboard

import kotlin.math.roundToInt

/**
 * Pure-function helpers that produce the numeric values
 * accessibility-string templates plug into for TalkBack
 * announcements (T209).
 *
 * Why this kernel exists rather than inlining
 * `speedKmh.coerceAtLeast(0.0).roundToInt()` at every Compose
 * call site: the visual readout (e.g.,
 * [com.lap.mobile.ui.dashboard.SpeedGauge]'s on-screen number)
 * and the accessibility readout (its contentDescription) MUST
 * compute identically. A divergence — sighted user sees "65"
 * while a TalkBack user hears "66" — is a silent accessibility
 * bug invisible to anyone who tests only one mode at a time.
 * Centralizing the rounding in a pure function means both sites
 * use the same contract; change rounding once, both stay in sync.
 *
 * Pure function — no Android, no time, no I/O — so the contract
 * is locked by JVM tests and the Compose layer is a thin
 * presentation surface that just feeds this value into a
 * stringResource template.
 */
object AccessibilitySpeechValues {
    /**
     * Defensively coerces [speedKmh] to ≥ 0 (so GPS jitter producing
     * small negatives doesn't announce as "-3 kilometers per hour"),
     * then rounds to the nearest integer per Kotlin's
     * [Double.roundToInt] contract: ties round towards positive
     * infinity (0.5 → 1, 65.5 → 66, 99.5 → 100).
     *
     * Returns the integer the accessibility template should plug
     * into the natural-language string (e.g.,
     * `R.string.accessibility_speed_format`).
     */
    fun roundSpeedForAnnouncement(speedKmh: Double): Int = speedKmh.coerceAtLeast(0.0).roundToInt()
}
