package com.lap.mobile.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the contract for [LapAnnouncementFormatter] (T207) — the
 * pure function that turns a completed lap (lap number + lap time
 * in ms) into the voice-announcement string TTS will speak when
 * the user has [com.lap.mobile.dashboard.VoiceAnnouncementPreference]
 * enabled.
 *
 * Why a different formatter than T194's [LapTimeFormatter]:
 * audience. T194 produces M:SS.mmm with millisecond precision for
 * the dashboard's visual readout (drivers compare lap-by-lap deltas
 * sub-second when reviewing). T207 produces M:SS.t (one decimal of
 * seconds) because:
 *   - voice announcements are heard, not read — driver can't
 *     distinguish 42.351s vs 42.342s aurally
 *   - speaking three decimal digits adds ~600ms of audio that
 *     keeps the driver's attention longer than necessary
 *   - one decimal matches how drivers describe lap times
 *     conversationally ("one forty two three" for 1:42.3)
 *
 * Why a different formatter than T202's [SessionElapsedFormatter]:
 * scale. Lap times rarely exceed 10 minutes; sessions span hours.
 * T202 auto-switches at 1 hour boundary (M:SS ↔ H:MM:SS); T207
 * always uses M:SS.t because a >60-minute lap is so unusual it
 * shouldn't drive format complexity.
 *
 * Format contract:
 *   - "Lap N — M:SS.t"
 *   - Lap number: integer, no zero-pad ("Lap 3", "Lap 10")
 *   - Em dash separator (—) per the T207 spec literal
 *   - Minutes: integer, no zero-pad ("1:42.3" not "01:42.3")
 *   - Seconds: two-digit zero-padded ("0:05.5" not "0:5.5") so
 *     spoken cadence stays consistent ("zero five point five"
 *     reads naturally)
 *   - Tenths: single digit, integer truncation of (ms % 1000) / 100
 *     — drops 0-99ms remainder rather than rounding because
 *     truncation is deterministic and sub-tenth precision is
 *     meaningless for audio
 *
 * Locale.US enforced for the same reason T194 and T202 enforce it:
 * the colon and period separators stay consistent across locales.
 *
 * Defensive cases (locked by tests):
 *   - Negative lap number → 0 (clock-skew or sentinel "no lap"
 *     value must not produce "Lap -1" garbled audio)
 *   - Negative lap time → "0:00.0" (same defensive pattern as
 *     T194 LapTimeFormatter, T202 SessionElapsedFormatter, T193
 *     SpeedGauge.coerceAtLeast(0.0))
 */
class LapAnnouncementFormatterTest {
    @Test
    fun headline_spec_example_one_minute_forty_two_point_three_renders_exactly_as_T207_spec_writes_it() {
        // The exact "Lap 3 — 1:42.3" the T207 acceptance spec
        // names. Locks the format contract drivers (eventually)
        // hear.
        assertEquals(
            "Lap 3 — 1:42.3",
            LapAnnouncementFormatter.format(lapNumber = 3, lapTimeMs = 102_345L),
        )
    }

    @Test
    fun exact_minute_renders_with_trailing_decimal_zero_so_spoken_cadence_stays_consistent_across_lap_times() {
        // 90_000 ms = exactly 1:30.0. The trailing ".0" must be
        // present so TTS speaks "one thirty point zero" — not
        // "one thirty" — keeping the cadence pattern identical
        // to non-exact-minute lap times.
        assertEquals(
            "Lap 1 — 1:30.0",
            LapAnnouncementFormatter.format(lapNumber = 1, lapTimeMs = 90_000L),
        )
    }

    @Test
    fun seconds_zero_pad_to_two_digits_so_sub_ten_second_remainder_doesnt_render_as_one_colon_five() {
        // 65_500 ms = 1:05.5. Seconds component must zero-pad to
        // "05" not "5" — keeps spoken cadence consistent ("one
        // oh five point five") and matches T194 LapTimeFormatter
        // / T202 SessionElapsedFormatter conventions.
        assertEquals(
            "Lap 10 — 1:05.5",
            LapAnnouncementFormatter.format(lapNumber = 10, lapTimeMs = 65_500L),
        )
    }

    @Test
    fun lap_number_does_not_zero_pad_so_short_first_lap_doesnt_render_as_lap_zero_one() {
        // Lap 1 must render as "Lap 1" not "Lap 01" — leading
        // zero would make TTS speak "lap zero one" which is
        // weird and not how drivers describe laps.
        assertEquals(
            "Lap 1 — 0:42.0",
            LapAnnouncementFormatter.format(lapNumber = 1, lapTimeMs = 42_000L),
        )
    }

    @Test
    fun sub_minute_lap_renders_with_zero_minutes_so_a_55_second_lap_is_zero_colon_55_point_t() {
        // 55_900 ms = 0:55.9. Minutes must show as "0" not blank
        // so cadence stays "zero fifty five point nine" not
        // "fifty five point nine" (which sounds like "55 of
        // something" without the unit context).
        assertEquals(
            "Lap 2 — 0:55.9",
            LapAnnouncementFormatter.format(lapNumber = 2, lapTimeMs = 55_900L),
        )
    }

    @Test
    fun ten_minute_lap_renders_minutes_field_as_two_digits_naturally_no_special_formatting_needed() {
        // 600_500 ms = 10:00.5. Minutes integer renders as "10"
        // naturally via %d. No M:SS ↔ H:MM:SS auto-switch like
        // T202 — laps don't span hours.
        assertEquals(
            "Lap 7 — 10:00.5",
            LapAnnouncementFormatter.format(lapNumber = 7, lapTimeMs = 600_500L),
        )
    }

    @Test
    fun tenths_truncate_not_round_so_a_lap_time_with_sub_tenth_remainder_doesnt_round_up() {
        // 102_399 ms = 1:42.399 → must render "1:42.3" (truncated
        // tenths, NOT rounded to "1:42.4"). Truncation is
        // deterministic; sub-tenth audio precision is meaningless.
        assertEquals(
            "Lap 5 — 1:42.3",
            LapAnnouncementFormatter.format(lapNumber = 5, lapTimeMs = 102_399L),
        )
    }

    @Test
    fun negative_lap_number_clamps_to_zero_so_sentinel_minus_one_no_lap_doesnt_garble_audio() {
        // Defensive: a sentinel "no lap yet" value of -1 must not
        // render as "Lap -1" which TTS would speak as "lap negative
        // one" — confusing and unprofessional. Clamp to 0.
        assertEquals(
            "Lap 0 — 1:00.0",
            LapAnnouncementFormatter.format(lapNumber = -1, lapTimeMs = 60_000L),
        )
    }

    @Test
    fun negative_lap_time_collapses_to_zero_zero_zero_so_clock_skew_doesnt_announce_negative_time() {
        // Defensive: same rationale as T194 LapTimeFormatter, T202
        // SessionElapsedFormatter, T193 SpeedGauge.coerceAtLeast.
        // A negative lap time (clock subtraction underflow, NaN
        // drift) must render as "0:00.0" not throw or announce
        // garbled audio.
        assertEquals(
            "Lap 5 — 0:00.0",
            LapAnnouncementFormatter.format(lapNumber = 5, lapTimeMs = -100L),
        )
    }
}
