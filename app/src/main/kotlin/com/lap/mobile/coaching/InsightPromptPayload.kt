package com.lap.mobile.coaching

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Severity tier for [InsightPromptPayload] driving the prompt
 * card's color theming and accessibility priority cue (T217).
 *
 * Three levels match the typical noise-floor → urgency mapping
 * for in-session coaching:
 *   - [LOW]: nice-to-know hint, neutral surface color
 *   - [MEDIUM]: standard coaching tip, accent color (default
 *     when payload omits severity or ships an unknown value)
 *   - [HIGH]: safety-relevant or repeated-error escalation,
 *     error-container color
 *
 * Forward-compat policy: an unknown severity string from the
 * server (e.g., new "critical" tier added later) falls back to
 * MEDIUM so the prompt still renders rather than silently
 * dropping. Critical-tier UI ships in a coordinated client
 * release later; until then the user sees the insight as a
 * normal-priority tip.
 */
enum class InsightSeverity { LOW, MEDIUM, HIGH }

/**
 * Renderable form of [CoachingInsight] that the bottom-sheet
 * Compose card (T217) consumes — typed extraction from the
 * opaque [CoachingInsight.payload] [JsonObject].
 *
 * Required fields are non-blank `title` and `body`. The opaque
 * JsonObject schema is owned by T217 (per T211's CoachingInsight
 * KDoc), so this data class IS the wire-format contract for the
 * payload from the server's rule-author UI:
 *
 * ```json
 * {
 *   "title": "Late braking at Turn 3",
 *   "body": "Try braking 30m earlier to carry more apex speed.",
 *   "severity": "medium"
 * }
 * ```
 *
 * Server-side YAML rule authoring + Pydantic schema (per ADR-013)
 * lives in lap-backend. The mobile contract is defined here.
 */
data class InsightPromptPayload(
    val ruleId: String,
    val title: String,
    val body: String,
    val severity: InsightSeverity,
)

/**
 * Extract a renderable [InsightPromptPayload] from a [CoachingInsight]
 * (T217 kernel — pure function, no Compose dependency).
 *
 * Returns `null` when required fields are missing, blank, or the
 * wrong JSON type. The composable layer skips rendering on null
 * rather than showing a half-broken card or crashing — a server
 * bug that emits a malformed payload should not interrupt the
 * recording UX (the rule won't fire useful coaching this lap, but
 * recording continues normally).
 *
 * Severity defaults to [InsightSeverity.MEDIUM] when missing,
 * blank, or an unknown string. See [InsightSeverity] KDoc for
 * the forward-compat rationale (server can add "critical" tier
 * without coordinated client release; until client supports it,
 * it renders as MEDIUM).
 */
fun parseInsightPromptPayload(insight: CoachingInsight): InsightPromptPayload? {
    val title = (insight.payload["title"] as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val body = (insight.payload["body"] as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.contentOrNull
        ?.takeIf { it.isNotEmpty() }
        ?: return null
    val severity = parseSeverityOrMedium(insight.payload["severity"])
    return InsightPromptPayload(
        ruleId = insight.ruleId,
        title = title,
        body = body,
        severity = severity,
    )
}

private fun parseSeverityOrMedium(raw: kotlinx.serialization.json.JsonElement?): InsightSeverity {
    val text = (raw as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        ?: return InsightSeverity.MEDIUM
    return when (text.lowercase()) {
        "low" -> InsightSeverity.LOW
        "medium" -> InsightSeverity.MEDIUM
        "high" -> InsightSeverity.HIGH
        else -> InsightSeverity.MEDIUM
    }
}
