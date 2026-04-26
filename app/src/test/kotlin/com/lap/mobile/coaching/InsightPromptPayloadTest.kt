package com.lap.mobile.coaching

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Locks the contract for [parseInsightPromptPayload] (T217 kernel)
 * — the pure-function extractor that converts an opaque
 * [CoachingInsight.payload] [JsonObject] into the typed
 * [InsightPromptPayload] consumed by [com.lap.mobile.ui.coaching.CoachingPromptCard].
 *
 * Coverage:
 *   - Happy path: full payload parses correctly
 *   - Required fields (title + body): missing or blank → null
 *     so the composable skips rendering rather than showing a
 *     broken card on server-side payload bugs
 *   - Severity: case-insensitive parse (low / Low / LOW),
 *     missing → MEDIUM default, unknown string → MEDIUM default
 *     (forward-compat with server adding new severity tiers)
 *   - Defensive against non-string JSON values for fields
 *
 * Why these tests are on the kernel not the composable:
 * @Composable functions need a Compose runtime to invoke; the
 * extraction logic doesn't. Putting the typed-extraction policy
 * in a pure function lets every malformed-payload path be tested
 * without Robolectric or instrumented tests. The composable just
 * does layout against typed [InsightPromptPayload] inputs.
 */
class InsightPromptPayloadTest {
    @Test
    fun parses_full_payload_with_title_body_and_severity_so_happy_path_renders_correctly() {
        val insight = insightWith(
            buildJsonObject {
                put("title", "Late braking at Turn 3")
                put("body", "Try braking 30m earlier to carry more apex speed.")
                put("severity", "high")
            },
        )

        val payload = parseInsightPromptPayload(insight)

        assertNotNull(payload)
        assertEquals("rule-late-braking", payload!!.ruleId)
        assertEquals("Late braking at Turn 3", payload.title)
        assertEquals("Try braking 30m earlier to carry more apex speed.", payload.body)
        assertEquals(InsightSeverity.HIGH, payload.severity)
    }

    @Test
    fun returns_null_when_title_field_missing_so_malformed_payload_doesnt_render_broken_card() {
        // Headline T217 invariant: a card with no title is broken
        // UX. Returning null lets the consumer (UI layer) skip
        // rendering for this insight rather than displaying garbage.
        val insight = insightWith(
            buildJsonObject {
                put("body", "body without a title is unrenderable")
                put("severity", "medium")
            },
        )

        assertNull(parseInsightPromptPayload(insight))
    }

    @Test
    fun returns_null_when_body_field_missing_so_unrenderable_payload_silently_skipped() {
        val insight = insightWith(
            buildJsonObject {
                put("title", "Title without body")
                put("severity", "medium")
            },
        )

        assertNull(parseInsightPromptPayload(insight))
    }

    @Test
    fun returns_null_when_title_is_blank_string_so_whitespace_only_doesnt_render_empty_header() {
        val insight = insightWith(
            buildJsonObject {
                put("title", "   ")
                put("body", "Real body")
            },
        )

        assertNull(parseInsightPromptPayload(insight))
    }

    @Test
    fun returns_null_when_body_is_empty_string_so_zero_length_doesnt_render_empty_body() {
        val insight = insightWith(
            buildJsonObject {
                put("title", "Real title")
                put("body", "")
            },
        )

        assertNull(parseInsightPromptPayload(insight))
    }

    @Test
    fun severity_defaults_to_medium_when_field_missing_so_omitted_severity_is_safe() {
        val insight = insightWith(
            buildJsonObject {
                put("title", "Title")
                put("body", "Body")
            },
        )

        val payload = parseInsightPromptPayload(insight)

        assertNotNull(payload)
        assertEquals(InsightSeverity.MEDIUM, payload!!.severity)
    }

    @Test
    fun severity_defaults_to_medium_when_value_is_unknown_string_so_forward_compat_with_server_added_tiers() {
        // Headline T217 forward-compat invariant: server may ship
        // a new severity tier (e.g., "critical") before the client
        // ships UI for it. Falling back to MEDIUM means the prompt
        // still renders rather than silently dropping the insight.
        val insight = insightWith(
            buildJsonObject {
                put("title", "Title")
                put("body", "Body")
                put("severity", "critical")
            },
        )

        val payload = parseInsightPromptPayload(insight)

        assertNotNull(payload)
        assertEquals(InsightSeverity.MEDIUM, payload!!.severity)
    }

    @Test
    fun severity_parse_is_case_insensitive_so_server_capitalisation_doesnt_break_rendering() {
        listOf("low", "Low", "LOW", "lOw").forEach { severityString ->
            val insight = insightWith(
                buildJsonObject {
                    put("title", "Title")
                    put("body", "Body")
                    put("severity", severityString)
                },
            )

            val payload = parseInsightPromptPayload(insight)

            assertNotNull("expected non-null for severity '$severityString'", payload)
            assertEquals(
                "case-insensitive parse failed for '$severityString'",
                InsightSeverity.LOW,
                payload!!.severity,
            )
        }
    }

    @Test
    fun severity_high_uppercase_parses_so_screaming_severity_strings_still_work() {
        val insight = insightWith(
            buildJsonObject {
                put("title", "Title")
                put("body", "Body")
                put("severity", "HIGH")
            },
        )

        val payload = parseInsightPromptPayload(insight)

        assertEquals(InsightSeverity.HIGH, payload!!.severity)
    }

    @Test
    fun severity_medium_explicit_parses_to_medium_so_default_can_be_explicitly_set() {
        val insight = insightWith(
            buildJsonObject {
                put("title", "Title")
                put("body", "Body")
                put("severity", "medium")
            },
        )

        val payload = parseInsightPromptPayload(insight)

        assertEquals(InsightSeverity.MEDIUM, payload!!.severity)
    }

    @Test
    fun returns_null_when_title_is_non_string_json_value_so_typed_payload_contract_is_defended() {
        // Defensive against server bug: if `title` is a number or
        // array (programming error in rule authoring tool), treat
        // as missing rather than coercing to "5" or similar garbage.
        val insight = insightWith(
            buildJsonObject {
                put("title", 42)
                put("body", "Body")
            },
        )

        assertNull(parseInsightPromptPayload(insight))
    }

    @Test
    fun severity_falls_back_to_medium_when_value_is_non_string_so_typed_contract_isnt_coerced() {
        val insight = insightWith(
            buildJsonObject {
                put("title", "Title")
                put("body", "Body")
                putJsonArray("severity") { add("medium") }
            },
        )

        val payload = parseInsightPromptPayload(insight)

        assertNotNull(payload)
        assertEquals(InsightSeverity.MEDIUM, payload!!.severity)
    }

    @Test
    fun returns_null_when_payload_is_empty_object_so_zero_field_payload_doesnt_render() {
        val insight = insightWith(buildJsonObject { })

        assertNull(parseInsightPromptPayload(insight))
    }

    @Test
    fun preserves_rule_id_from_insight_so_dismissal_callbacks_can_reference_originating_rule() {
        // Headline T217 invariant: the rule_id round-trips through
        // the renderer so T219's dismissal + flag-as-wrong handler
        // can store per-rule preferences keyed by ID.
        val insight = CoachingInsight(
            ruleId = "br.late_braking.apex_speed_loss",
            payload = buildJsonObject {
                put("title", "Title")
                put("body", "Body")
            },
        )

        val payload = parseInsightPromptPayload(insight)

        assertEquals("br.late_braking.apex_speed_loss", payload!!.ruleId)
    }

    private fun insightWith(payload: JsonObject): CoachingInsight = CoachingInsight(
        ruleId = "rule-late-braking",
        payload = payload,
    )
}
