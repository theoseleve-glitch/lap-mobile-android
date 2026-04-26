package com.lap.mobile.coaching

/**
 * Pure formatter producing the spoken text for a coaching insight
 * (T218 kernel — no Android dependency, no TTS engine, no Compose).
 *
 * Splits the audio cue into two tiers driven by [InsightSeverity]:
 *   - [InsightSeverity.HIGH]   → uses [urgentPrefix] ("Important
 *     coaching: ...") so safety-relevant or repeated-error escalations
 *     get an audible urgency cue distinct from routine tips.
 *   - [InsightSeverity.MEDIUM] / [InsightSeverity.LOW] → uses
 *     [defaultPrefix] ("Coaching: ...") so the bulk of tips share a
 *     calm framing — the driver shouldn't hear "important" every lap
 *     or it stops meaning anything.
 *
 * Body text is intentionally NOT spoken — it's the visual reader's
 * detail, while the audio cue is meant to be a glance-replacement
 * (driver hears the headline, looks at the screen for the
 * explanation if needed). Long bodies would interrupt the driver's
 * focus far longer than a short title, which defeats the purpose
 * of the cue.
 *
 * Why prefixes are parameters not English constants: keeps this
 * formatter pure JVM-testable. The Android TTS speaker (future
 * task) pulls localized strings from `R.string.coaching_audio_cue_*`
 * and passes them in. JVM tests pass plain strings.
 *
 * Pre-condition: [payload]'s title is non-blank (already guaranteed
 * by [parseInsightPromptPayload], which returns null for blank
 * titles). The formatter doesn't re-validate — calling it with
 * a blank-title payload is a contract violation by the caller.
 */
object CoachingAudioCueFormatter {
    fun format(payload: InsightPromptPayload, defaultPrefix: String, urgentPrefix: String): String {
        val prefix = when (payload.severity) {
            InsightSeverity.HIGH -> urgentPrefix
            InsightSeverity.MEDIUM, InsightSeverity.LOW -> defaultPrefix
        }
        return if (prefix.isBlank()) payload.title else "$prefix ${payload.title}"
    }
}
