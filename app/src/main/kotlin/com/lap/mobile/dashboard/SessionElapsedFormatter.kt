package com.lap.mobile.dashboard

import java.util.Locale

/**
 * Formats elapsed milliseconds into the session-timer string for
 * the dashboard's recording-state strip (T202).
 *
 * Auto-switches format based on duration:
 *   - Under 1 hour: M:SS (e.g., "0:42", "12:34", "59:59").
 *   - 1 hour or more: H:MM:SS (e.g., "1:00:00", "1:30:45",
 *     "23:00:00").
 *
 * The switch threshold is INCLUSIVE at the hour boundary so a
 * 1:00:01 timer doesn't jump from "60:01" to "1:00:01" — once
 * the hour digit appears, it stays.
 *
 * Why a different formatter than T194's [LapTimeFormatter]:
 * scale. Lap times need millisecond precision (M:SS.mmm) because
 * lap-by-lap deltas are sub-second. Session times span minutes-
 * to-hours and the strip glance question is "how long have I
 * been recording?" — ms digits would jitter and add no
 * actionable information.
 *
 * Locale.US enforced for the same reason T194 enforces it: the
 * colon separator stays consistent across locales, and digit
 * grouping doesn't sneak in a thousands separator.
 *
 * Defensive: negative inputs collapse to "0:00" via
 * coerceAtLeast(0L). Same rationale as T194 LapTimeFormatter
 * and T193 SpeedGauge — clock skew or NaN drift must not render
 * as invalid format or throw.
 */
object SessionElapsedFormatter {
    fun format(elapsedMs: Long): String {
        val total = elapsedMs.coerceAtLeast(0L)
        val hours = total / MS_PER_HOUR
        val minutes = (total % MS_PER_HOUR) / MS_PER_MINUTE
        val seconds = (total % MS_PER_MINUTE) / MS_PER_SECOND
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    private const val MS_PER_SECOND: Long = 1_000L
    private const val MS_PER_MINUTE: Long = 60_000L
    private const val MS_PER_HOUR: Long = 3_600_000L
}
