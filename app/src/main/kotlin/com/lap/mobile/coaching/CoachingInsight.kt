package com.lap.mobile.coaching

import kotlinx.serialization.json.JsonObject

/**
 * One rule firing — the unit T217's bottom-sheet prompt
 * Composable consumes (T211 — port of the desktop Python
 * `datalogger.coaching.rules_engine.CoachingInsight`).
 *
 * Produced by [T214's evaluator] when [Rule.condition] evaluates
 * truthy against a session-feature snapshot. [payload] is the
 * rule's verbatim [Rule.insight] dict — opaque to the engine,
 * owned by the renderer.
 *
 * Plain data class (no @Serializable yet — insights are produced
 * locally and consumed by the UI; they don't need to round-trip
 * to disk or wire). T215's session-coordinator hook will write
 * them to the bundle's `coaching_insights.json` for server-side
 * audit (per ADR-009 — server is authoritative; client coaching
 * is best-effort latency cover) — that wiring task adds the
 * @Serializable annotation if needed.
 *
 * No schema field — distinct from server-side post-session
 * insights, which T215 will tag with `source: "client"` to keep
 * the audit trail clean.
 */
data class CoachingInsight(
    val ruleId: String,
    val payload: JsonObject,
)
