package com.lap.mobile.coaching

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the contract for [CoachingAudioCueFormatter] (T218 kernel).
 *
 * Coverage:
 *   - HIGH severity uses the urgent prefix (audible urgency cue
 *     distinct from routine tips)
 *   - MEDIUM and LOW severities both use the default prefix (the
 *     bulk of tips share a calm framing so "important" stays
 *     meaningful when it does appear)
 *   - Title preserved verbatim including punctuation and special
 *     characters (TTS handles them, formatter doesn't strip)
 *   - Empty prefix degrades gracefully (formatter doesn't emit a
 *     leading space — defensive against caller passing blank
 *     localized strings during partial-translation rollouts)
 *
 * Headline T218 invariant: HIGH severity must produce a strictly
 * different output from MEDIUM/LOW so audio-only consumers (driver
 * not looking at screen) hear the urgency tier without needing to
 * glance.
 */
class CoachingAudioCueFormatterTest {
    @Test
    fun high_severity_uses_urgent_prefix_so_safety_critical_tips_are_audibly_distinct() {
        val payload = payloadWith(InsightSeverity.HIGH, "Trail brake overshoot")

        val spoken = CoachingAudioCueFormatter.format(
            payload,
            defaultPrefix = "Coaching:",
            urgentPrefix = "Important coaching:",
        )

        assertEquals("Important coaching: Trail brake overshoot", spoken)
    }

    @Test
    fun medium_severity_uses_default_prefix_so_routine_tips_share_calm_framing() {
        val payload = payloadWith(InsightSeverity.MEDIUM, "Late braking at Turn 3")

        val spoken = CoachingAudioCueFormatter.format(
            payload,
            defaultPrefix = "Coaching:",
            urgentPrefix = "Important coaching:",
        )

        assertEquals("Coaching: Late braking at Turn 3", spoken)
    }

    @Test
    fun low_severity_uses_default_prefix_so_low_priority_doesnt_pretend_to_be_urgent() {
        val payload = payloadWith(InsightSeverity.LOW, "Throttle modulation at Turn 5")

        val spoken = CoachingAudioCueFormatter.format(
            payload,
            defaultPrefix = "Coaching:",
            urgentPrefix = "Important coaching:",
        )

        assertEquals("Coaching: Throttle modulation at Turn 5", spoken)
    }

    @Test
    fun high_severity_strictly_differs_from_medium_so_audio_only_users_hear_the_urgency_tier() {
        // Headline T218 invariant: the spoken text for HIGH must
        // not equal the spoken text for MEDIUM with the same title.
        // If they ever collide (e.g., a future refactor unifies the
        // prefix), an audio-only consumer loses the urgency signal.
        val title = "Same title for both severities"
        val high = CoachingAudioCueFormatter.format(
            payloadWith(InsightSeverity.HIGH, title),
            defaultPrefix = "Coaching:",
            urgentPrefix = "Important coaching:",
        )
        val medium = CoachingAudioCueFormatter.format(
            payloadWith(InsightSeverity.MEDIUM, title),
            defaultPrefix = "Coaching:",
            urgentPrefix = "Important coaching:",
        )

        assert(high != medium) {
            "Expected HIGH and MEDIUM spoken text to differ; both were '$high'"
        }
    }

    @Test
    fun title_preserved_verbatim_including_punctuation_so_tts_speaks_what_rule_author_wrote() {
        val payload = payloadWith(
            InsightSeverity.MEDIUM,
            "You're carrying brake — release earlier!",
        )

        val spoken = CoachingAudioCueFormatter.format(
            payload,
            defaultPrefix = "Coaching:",
            urgentPrefix = "Important coaching:",
        )

        assertEquals("Coaching: You're carrying brake — release earlier!", spoken)
    }

    @Test
    fun blank_default_prefix_returns_title_only_so_partial_localisation_doesnt_emit_leading_space() {
        val payload = payloadWith(InsightSeverity.MEDIUM, "Bare title")

        val spoken = CoachingAudioCueFormatter.format(
            payload,
            defaultPrefix = "",
            urgentPrefix = "Important coaching:",
        )

        assertEquals("Bare title", spoken)
    }

    @Test
    fun blank_urgent_prefix_returns_title_only_for_high_so_partial_localisation_degrades_gracefully() {
        val payload = payloadWith(InsightSeverity.HIGH, "Critical title")

        val spoken = CoachingAudioCueFormatter.format(
            payload,
            defaultPrefix = "Coaching:",
            urgentPrefix = "",
        )

        assertEquals("Critical title", spoken)
    }

    private fun payloadWith(severity: InsightSeverity, title: String): InsightPromptPayload = InsightPromptPayload(
        ruleId = "rule-test",
        title = title,
        body = "Body text not spoken — formatter only uses title.",
        severity = severity,
    )
}
