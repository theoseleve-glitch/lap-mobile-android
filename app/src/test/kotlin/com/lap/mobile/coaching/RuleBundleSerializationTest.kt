package com.lap.mobile.coaching

import com.lap.mobile.core.lapJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the wire-format contract for [Rule] and [RuleBundle] (T211)
 * — the Kotlin types must round-trip through the same JSON shape
 * the desktop Python source-of-truth (datalogger/coaching/rules_engine.py)
 * produces, so a single backend-emitted bundle decodes identically
 * on both clients.
 *
 * Why JVM tests at the interface layer (T211): if the @SerialName
 * mappings drift even slightly (e.g., a refactor accidentally
 * dropping @SerialName on `min_corroborating_laps`), every later
 * task in Block K breaks silently — T212's fetcher decodes a bundle
 * with default field values, T214's evaluator never fires anything,
 * T217's prompt UI never renders. Locking the wire format here
 * prevents the whole-block regression.
 *
 * Field naming: the bundle is server-authored and produced by the
 * Pydantic schema in `lap-backend/coaching/library/rule_schema.py`.
 * Python defaults to snake_case; Kotlin uses camelCase. Each
 * relevant field carries an explicit @SerialName so the wire
 * format stays snake_case independent of the Kotlin field name.
 *
 * Forward-compat: [lapJson] is configured with
 * `ignoreUnknownKeys = true` (per the project's Json setup), so
 * unknown top-level / per-rule keys silently parse — the server
 * can ship metadata (signatures, telemetry hints, etc.) without
 * a coordinated client release. Same forward-compat guarantee the
 * desktop spec documents.
 *
 * Encode-defaults policy: [lapJson] has `encodeDefaults = false`,
 * so a [Rule] with the default `minCorroboratingLaps = 1` omits the
 * field on encode. This keeps over-the-wire bundles compact (most
 * rules don't override the corroboration count).
 */
class RuleBundleSerializationTest {
    @Test
    fun bundle_round_trips_through_lap_json_so_serialization_contract_is_lossless() {
        // Headline contract: encode → decode must reproduce the
        // input exactly. If this test breaks, every downstream
        // consumer (T212 fetch, T213 cache, T214 evaluate) decodes
        // garbage on the next bundle.
        val condition = buildJsonObject {
            put("type", "feature_threshold")
            put("feature", "apex_speed")
            put("comparison", "lt")
            put("value", 80.0)
        }
        val insight = buildJsonObject {
            put("category", "braking")
            put("severity", "medium")
        }
        val original = RuleBundle(
            schemaVersion = "1.0.0",
            bundleId = "bundle-2026-04-26",
            compiledAt = "2026-04-26T08:00:00Z",
            rules = listOf(
                Rule(
                    id = "br.late_braking.apex_speed_loss",
                    category = "technique",
                    condition = condition,
                    insight = insight,
                    minCorroboratingLaps = 3,
                ),
            ),
        )

        val encoded = lapJson.encodeToString(original)
        val decoded = lapJson.decodeFromString<RuleBundle>(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun bundle_decodes_from_snake_case_payload_so_python_desktop_format_stays_in_sync() {
        // Anchor against the Python source-of-truth wire format.
        // Hand-written snake_case JSON that mirrors what
        // datalogger/coaching/rules_engine.py would emit. If a
        // future @SerialName drops accidentally, this test fails
        // before the backend integration ever sees it.
        val payload = """
            {
              "schema_version": "1.0.0",
              "bundle_id": "bundle-2026-04-26",
              "compiled_at": "2026-04-26T08:00:00Z",
              "rules": [
                {
                  "id": "br.late_braking.apex_speed_loss",
                  "category": "technique",
                  "condition": {"type": "feature_threshold", "feature": "apex_speed", "comparison": "lt", "value": 80.0},
                  "insight": {"category": "braking", "severity": "medium"},
                  "min_corroborating_laps": 3
                }
              ]
            }
        """.trimIndent()

        val bundle = lapJson.decodeFromString<RuleBundle>(payload)

        assertEquals("1.0.0", bundle.schemaVersion)
        assertEquals("bundle-2026-04-26", bundle.bundleId)
        assertEquals("2026-04-26T08:00:00Z", bundle.compiledAt)
        assertEquals(1, bundle.rules.size)
        val rule = bundle.rules.single()
        assertEquals("br.late_braking.apex_speed_loss", rule.id)
        assertEquals("technique", rule.category)
        assertEquals(3, rule.minCorroboratingLaps)
        assertTrue(rule.condition is JsonObject)
        assertTrue(rule.insight is JsonObject)
    }

    @Test
    fun rule_with_default_corroboration_omits_field_when_encoded_so_wire_payload_stays_compact() {
        // lapJson is configured with encodeDefaults = false. A
        // rule using the default minCorroboratingLaps = 1 must
        // NOT serialize the field — saves bytes when the bundle
        // has 80-150 rules (most use the default).
        val emptyObject: JsonObject = buildJsonObject { }
        val rule = Rule(
            id = "test.rule",
            category = "technique",
            condition = emptyObject,
            insight = emptyObject,
            // minCorroboratingLaps defaults to 1.
        )

        val encoded = lapJson.encodeToString(rule)

        assertFalse(
            "Encoded rule should not contain min_corroborating_laps " +
                "when value is the default. Encoded: $encoded",
            encoded.contains("min_corroborating_laps"),
        )
    }

    @Test
    fun rule_without_min_corroborating_laps_field_decodes_with_default_so_omitted_field_is_treated_as_one() {
        // Counterpart to the encode test — a payload with
        // min_corroborating_laps absent must decode to the default
        // 1 (not 0, not null, not a parse error). Locks the
        // forward-compat semantics: server can omit the field for
        // all rules that use the default and the client still
        // works.
        val payload = """
            {
              "id": "test.rule",
              "category": "technique",
              "condition": {},
              "insight": {}
            }
        """.trimIndent()

        val rule = lapJson.decodeFromString<Rule>(payload)

        assertEquals(1, rule.minCorroboratingLaps)
    }

    @Test
    fun unknown_keys_in_bundle_are_ignored_so_server_can_add_metadata_without_coordinated_client_release() {
        // Forward-compat: lapJson has ignoreUnknownKeys = true.
        // The server can add fields (signature, content_hash, etc.)
        // and old clients keep working. Without this, a single
        // server-side metadata addition breaks every deployed app.
        val payload = """
            {
              "schema_version": "1.0.0",
              "bundle_id": "bundle-2026-04-26",
              "compiled_at": "2026-04-26T08:00:00Z",
              "rules": [],
              "signature": "abc123",
              "future_telemetry_hint": {"foo": "bar"}
            }
        """.trimIndent()

        val bundle = lapJson.decodeFromString<RuleBundle>(payload)

        assertEquals("bundle-2026-04-26", bundle.bundleId)
    }
}
