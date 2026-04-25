package com.lap.mobile.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the contract for [SessionElapsedFormatter] (T202) — the
 * pure function that turns elapsed milliseconds into a glance-
 * readable session-timer string for the
 * [com.lap.mobile.ui.dashboard.RecordingStateStrip].
 *
 * Why a different formatter than T194's [LapTimeFormatter]: scale.
 * Lap times need millisecond precision (M:SS.mmm) because lap-by-
 * lap deltas are sub-second. Session times span minutes-to-hours
 * and the strip glance question is "how long have I been
 * recording?" — millisecond digits would jitter and add no
 * actionable information.
 *
 * Format invariants:
 *   - Under 1 hour: M:SS — minutes don't zero-pad, seconds always
 *     do. "0:42", "12:34", "59:59".
 *   - 1 hour or more: H:MM:SS — hours don't zero-pad, minutes and
 *     seconds always do. "1:00:00", "1:30:45", "23:59:59".
 *   - Decimal separator: implicit (no decimals in this format).
 *   - Locale.US for the format calls so the colon stays a colon
 *     and digit grouping doesn't interfere.
 *
 * The auto-switch at 1 hour matches how drivers describe sessions
 * — "we ran for 45 minutes" / "we ran for 2 hours". H:MM:SS for
 * sub-hour sessions wastes glyphs on zeros that don't change.
 *
 * Defensive: negative inputs collapse to "0:00" (same rationale as
 * T194 LapTimeFormatter and T193 SpeedGauge — clock skew or NaN
 * drift must not render as ":-:-:-:" or throw).
 */
class SessionElapsedFormatterTest {
    @Test
    fun zero_ms_is_pre_session_default_so_idle_to_recording_transition_starts_at_0_colon_00() {
        assertEquals("0:00", SessionElapsedFormatter.format(elapsedMs = 0L))
    }

    @Test
    fun thirty_seconds_renders_as_zero_colon_thirty_so_brief_session_shows_seconds_growing() {
        assertEquals("0:30", SessionElapsedFormatter.format(elapsedMs = 30_000L))
    }

    @Test
    fun exactly_one_minute_carries_into_minutes_field_and_seconds_zero_pad_to_two_digits() {
        // 60_000ms = 1 minute exactly. Locks "1:00" not "0:60",
        // and seconds zero-pad ("1:00" not "1:0").
        assertEquals("1:00", SessionElapsedFormatter.format(elapsedMs = 60_000L))
    }

    @Test
    fun typical_short_session_one_minute_thirty_seconds_renders_as_one_colon_thirty() {
        assertEquals("1:30", SessionElapsedFormatter.format(elapsedMs = 90_000L))
    }

    @Test
    fun fifty_nine_minutes_fifty_nine_seconds_doesnt_jump_to_hours_format_just_below_threshold() {
        // 59:59 must stay in MM:SS — auto-switch is at exactly 1
        // hour, not "approaching". Boundary lock.
        assertEquals("59:59", SessionElapsedFormatter.format(elapsedMs = 3_599_000L))
    }

    @Test
    fun exactly_one_hour_switches_to_h_colon_mm_colon_ss_format_so_endurance_sessions_get_the_third_digit() {
        // 3_600_000ms = 1h exactly. Switch threshold INCLUSIVE at
        // hour boundary so a 1:00:01 timer doesn't jump from "60:01"
        // to "1:00:01".
        assertEquals("1:00:00", SessionElapsedFormatter.format(elapsedMs = 3_600_000L))
    }

    @Test
    fun one_hour_thirty_minutes_forty_five_seconds_renders_as_one_colon_thirty_colon_forty_five() {
        // Headline T202 long-session invariant.
        val ms = 3_600_000L + 30 * 60_000L + 45 * 1_000L

        assertEquals("1:30:45", SessionElapsedFormatter.format(elapsedMs = ms))
    }

    @Test
    fun twenty_three_hour_session_doesnt_overflow_or_truncate_so_endurance_event_stays_readable() {
        // Long endurance event support — no upper cap on hours.
        val ms = 23L * 3_600_000L

        assertEquals("23:00:00", SessionElapsedFormatter.format(elapsedMs = ms))
    }

    @Test
    fun minutes_dont_zero_pad_in_short_format_so_a_one_minute_session_doesnt_render_as_zero_one() {
        // Short-format minutes are "1:00" not "01:00" — wastes a
        // glyph and visually implies "01" is a special prefix.
        // Same convention T194 LapTimeFormatter uses.
        assertEquals("1:00", SessionElapsedFormatter.format(elapsedMs = 60_000L))
    }

    @Test
    fun negative_elapsed_collapses_to_zero_colon_zero_zero_so_clock_skew_doesnt_render_invalid_format() {
        // Defensive: same rationale as T194 LapTimeFormatter and
        // T193 SpeedGauge.coerceAtLeast(0.0). A future race-clock
        // subtraction yielding -50ms must not render as ":-:-:-".
        assertEquals("0:00", SessionElapsedFormatter.format(elapsedMs = -50L))
    }
}
