package com.lap.mobile.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the contract for [LapTimeFormatter] (T194) — the pure
 * function that turns elapsed milliseconds into the canonical
 * motorsport-format `M:SS.mmm` string for the dashboard's lap-timer.
 *
 * Why a kernel split (formatter separate from the Compose display):
 * "M:SS.mmm" is the universally-recognized motorsport convention.
 * Lap times are never localized — `1:23.456` reads the same to a
 * driver in Tokyo, Madrid, or Detroit. JVM-testing the formatter
 * locks the format against future drift (e.g., a future Compose
 * refactor accidentally introducing locale-aware decimal commas).
 *
 * Format invariants:
 *   - Minutes: zero-suffixed, no leading zero (1:00.000 not 01:00.000).
 *   - Seconds: zero-padded to 2 digits (1:05.000 not 1:5.000).
 *   - Milliseconds: zero-padded to 3 digits (1:00.005 not 1:00.5).
 *   - Decimal separator is `.` not `,` (English / motorsport).
 *
 * Defensive: negative inputs collapse to "0:00.000" — same rationale
 * as T193's SpeedGauge coerceAtLeast(0.0). Elapsed time is never
 * negative in practice, but a future race-clock subtraction yielding
 * -50ms (clock skew) must not render as ":-:-.- " or crash.
 */
class LapTimeFormatterTest {
    @Test
    fun zero_ms_is_pre_session_default_so_cold_launch_timer_reads_zero_zero_zero() {
        assertEquals("0:00.000", LapTimeFormatter.format(elapsedMs = 0L))
    }

    @Test
    fun sub_second_elapsed_pads_milliseconds_to_three_digits_so_999ms_is_zero_dot_999_not_dot_9() {
        assertEquals("0:00.999", LapTimeFormatter.format(elapsedMs = 999L))
    }

    @Test
    fun exactly_one_second_carries_into_seconds_field_so_1000ms_is_zero_colon_zero_one_dot_zero_zero_zero() {
        assertEquals("0:01.000", LapTimeFormatter.format(elapsedMs = 1_000L))
    }

    @Test
    fun seconds_pad_to_two_digits_so_5_seconds_is_zero_colon_zero_five_not_zero_colon_five() {
        assertEquals("0:05.000", LapTimeFormatter.format(elapsedMs = 5_000L))
    }

    @Test
    fun exactly_one_minute_carries_into_minutes_field_so_60000ms_is_one_colon_zero_zero_dot_zero_zero_zero() {
        assertEquals("1:00.000", LapTimeFormatter.format(elapsedMs = 60_000L))
    }

    @Test
    fun typical_lap_time_one_min_27s_415ms_renders_as_1_colon_27_dot_415_so_T192_preview_value_round_trips() {
        // T192's preview live state used 87_415ms — same value here
        // locks the formatter against the preview fixture.
        assertEquals("1:27.415", LapTimeFormatter.format(elapsedMs = 87_415L))
    }

    @Test
    fun minutes_dont_zero_pad_so_a_one_minute_lap_doesnt_render_as_zero_one_dash() {
        // Drivers expect "1:23.456" not "01:23.456" — the latter
        // wastes a glyph that could be used for the seconds digit.
        // Locks the convention.
        assertEquals("1:23.456", LapTimeFormatter.format(elapsedMs = 83_456L))
    }

    @Test
    fun long_endurance_laps_format_correctly_so_a_ten_minute_lap_doesnt_overflow_or_truncate() {
        // Endurance racing has 10+ minute stints between pit stops.
        // The minutes field has no upper cap.
        assertEquals("10:00.000", LapTimeFormatter.format(elapsedMs = 600_000L))
    }

    @Test
    fun negative_elapsed_collapses_to_zero_so_clock_skew_doesnt_render_invalid_format_or_crash() {
        // Defensive: same rationale as T193's coerceAtLeast(0.0).
        // A future race-clock subtraction yielding -50ms (clock skew,
        // GPS time vs system time) must render as "0:00.000" not
        // ":-:-.- " or throw.
        assertEquals("0:00.000", LapTimeFormatter.format(elapsedMs = -50L))
    }
}
