package com.lap.mobile.dashboard

import java.util.Locale

/**
 * Formats elapsed milliseconds into the canonical motorsport
 * `M:SS.mmm` lap-time string (T194).
 *
 * Pure function — no Android, no time, no I/O — so the format is
 * locked by JVM unit tests and the Compose
 * [com.lap.mobile.ui.dashboard.LapDisplay] consumer can't drift.
 *
 * Format invariants:
 *   - Minutes: no leading zero (1:00.000 not 01:00.000).
 *   - Seconds: zero-padded to 2 digits (1:05.000 not 1:5.000).
 *   - Milliseconds: zero-padded to 3 digits (1:00.005 not 1:00.5).
 *   - Decimal separator is always `.` (motorsport / English),
 *     enforced by [Locale.US] — without it, `String.format` would
 *     emit a comma in some locales (e.g. fr-FR), and a French driver
 *     reading "1:23,456" would see something that no live-timing
 *     screen anywhere on the planet uses.
 *
 * Defensive: negative inputs collapse to "0:00.000" via
 * `coerceAtLeast(0L)` — same rationale as T193's
 * SpeedGauge.coerceAtLeast(0.0). A future race-clock subtraction
 * yielding -50ms (clock skew, GPS time vs system time) must not
 * render as ":-:-.- " or throw.
 */
object LapTimeFormatter {
    fun format(elapsedMs: Long): String {
        val total = elapsedMs.coerceAtLeast(0L)
        val minutes = total / MS_PER_MINUTE
        val seconds = (total % MS_PER_MINUTE) / MS_PER_SECOND
        val millis = total % MS_PER_SECOND
        return String.format(Locale.US, "%d:%02d.%03d", minutes, seconds, millis)
    }

    private const val MS_PER_SECOND: Long = 1_000L
    private const val MS_PER_MINUTE: Long = 60_000L
}
