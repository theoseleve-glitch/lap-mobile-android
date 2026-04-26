package com.lap.mobile.dashboard

import java.util.Locale

/**
 * Formats a completed lap (lap number + lap time in ms) into the
 * voice-announcement string that TTS will speak when the user has
 * [VoiceAnnouncementPreference] enabled (T207).
 *
 * Output shape: `"Lap N — M:SS.t"` per the T207 spec example
 * `"Lap 3 — 1:42.3"`.
 *
 * Why a different formatter than T194's [LapTimeFormatter]:
 * audience. T194 produces M:SS.mmm with millisecond precision for
 * the dashboard's visual readout — drivers compare lap-by-lap
 * deltas sub-second when reviewing. T207 produces M:SS.t (one
 * decimal of seconds) because:
 *   - voice announcements are heard, not read — driver can't
 *     distinguish 42.351s vs 42.342s aurally
 *   - speaking three decimal digits adds ~600ms of audio that
 *     keeps the driver's attention longer than necessary
 *   - one decimal matches how drivers describe lap times
 *     conversationally
 *
 * Why a different formatter than T202's [SessionElapsedFormatter]:
 * scale. Lap times rarely exceed 10 minutes; sessions span hours.
 * T202 auto-switches at 1 hour boundary; T207 always uses M:SS.t
 * because a >60-minute lap is so unusual it shouldn't drive format
 * complexity.
 *
 * Tenths use **integer truncation**, not rounding: `(ms % 1000) / 100`
 * drops the 0-99ms remainder rather than rounding it. Truncation is
 * deterministic (no IEEE 754 / banker's-rounding ambiguity), and
 * sub-tenth audio precision is meaningless for voice announcements.
 *
 * Locale.US enforced for the same reason T194 and T202 enforce it:
 * the colon and period separators stay consistent across locales,
 * and digit grouping doesn't sneak in a thousands separator.
 *
 * Defensive: negative lap number clamps to 0 (sentinel "no lap"
 * value of -1 must not render as "Lap -1" which TTS would speak as
 * "lap negative one"); negative lap time collapses to "0:00.0"
 * (same defensive pattern as T194 LapTimeFormatter, T202
 * SessionElapsedFormatter, T193 SpeedGauge.coerceAtLeast(0.0) —
 * clock skew or NaN drift must not throw or render garbled audio).
 */
object LapAnnouncementFormatter {
    fun format(lapNumber: Int, lapTimeMs: Long): String {
        val safeLap = lapNumber.coerceAtLeast(0)
        val safeMs = lapTimeMs.coerceAtLeast(0L)
        val minutes = safeMs / MS_PER_MINUTE
        val remainingMs = safeMs % MS_PER_MINUTE
        val seconds = remainingMs / MS_PER_SECOND
        val tenths = (remainingMs % MS_PER_SECOND) / MS_PER_TENTH
        return String.format(
            Locale.US,
            "Lap %d — %d:%02d.%d",
            safeLap,
            minutes,
            seconds,
            tenths,
        )
    }

    private const val MS_PER_TENTH: Long = 100L
    private const val MS_PER_SECOND: Long = 1_000L
    private const val MS_PER_MINUTE: Long = 60_000L
}
