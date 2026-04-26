package com.lap.mobile.coaching

import com.lap.mobile.core.lapJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the contract for [subsetForMobileRealLife] (T216) — the
 * post-load filter that drops rules not tagged `"mobile_real_life"`
 * to reduce evaluator iteration count + allocation per session-end.
 *
 * Coverage:
 *   - Mixed-tag bundle filters down to mobile_real_life rules only
 *   - Untagged rules excluded (STRICT opt-in semantics — a rule
 *     with NO applicable_to tag is not safe-by-default for mobile)
 *   - Multi-tag rules ([mobile_real_life, desktop]) included
 *   - Empty bundle filters to empty bundle (no crash, no metadata loss)
 *   - Bundle metadata preserved (schemaVersion, bundleId, compiledAt)
 *   - Filter preserves rule order
 *
 * Plus serialization tests for the new applicable_to field on
 * [Rule] — round-trip + decode-from-snake-case + default-empty
 * omits-on-encode (mirrors the existing min_corroborating_laps
 * tests so the wire-format contract stays tight).
 */
class RuleBundleSubsetTest {
    @Test
    fun subset_keeps_only_mobile_real_life_tagged_rules_so_desktop_only_rules_dont_run_in_recording_session() {
        // Headline T216 invariant: server bundle has rules tagged
        // for various platforms; only those opted into the mobile
        // recording context survive the filter.
        val bundle = RuleBundle(
            schemaVersion = "1.0.0",
            bundleId = "test-bundle",
            compiledAt = "2026-04-26T08:00:00Z",
            rules = listOf(
                ruleWithTags("rule-mobile-only", listOf(MOBILE_REAL_LIFE_TAG)),
                ruleWithTags("rule-desktop-only", listOf("desktop")),
                ruleWithTags("rule-simulator-only", listOf("simulator")),
                ruleWithTags("rule-mobile-and-desktop", listOf(MOBILE_REAL_LIFE_TAG, "desktop")),
            ),
        )

        val subset = subsetForMobileRealLife(bundle)

        assertEquals(
            listOf("rule-mobile-only", "rule-mobile-and-desktop"),
            subset.rules.map { it.id },
        )
    }

    @Test
    fun subset_excludes_rules_with_no_applicable_to_tag_so_strict_opt_in_prevents_accidental_mobile_fire() {
        // Headline T216 defense: a rule with NO applicable_to tag
        // could be a desktop-tuned-but-not-yet-tagged rule. Erring
        // on the side of exclusion forces server authors to consciously
        // opt rules into mobile rather than getting surprises from
        // un-vetted thresholds firing in the foreground service.
        val bundle = RuleBundle(
            schemaVersion = "1.0.0",
            bundleId = "test-bundle",
            compiledAt = "2026-04-26T08:00:00Z",
            rules = listOf(
                ruleWithTags("rule-untagged-1", emptyList()),
                ruleWithTags("rule-untagged-2", emptyList()),
                ruleWithTags("rule-mobile", listOf(MOBILE_REAL_LIFE_TAG)),
            ),
        )

        val subset = subsetForMobileRealLife(bundle)

        assertEquals(listOf("rule-mobile"), subset.rules.map { it.id })
    }

    @Test
    fun subset_includes_rule_with_multiple_platform_tags_when_mobile_real_life_is_one_of_them() {
        val bundle = RuleBundle(
            schemaVersion = "1.0.0",
            bundleId = "test-bundle",
            compiledAt = "2026-04-26T08:00:00Z",
            rules = listOf(
                ruleWithTags(
                    "rule-multi-platform",
                    listOf("desktop", MOBILE_REAL_LIFE_TAG, "simulator"),
                ),
            ),
        )

        val subset = subsetForMobileRealLife(bundle)

        assertEquals(1, subset.rules.size)
        assertEquals("rule-multi-platform", subset.rules.single().id)
    }

    @Test
    fun subset_preserves_bundle_metadata_so_audit_log_can_still_attribute_to_source_bundle_id() {
        // Headline T216 invariant: subset is the same logical
        // bundle with fewer rules, NOT a derived bundle with a
        // new identity. T215 audit writer needs source bundleId
        // and compiledAt to stay correct on the post-filter bundle.
        val bundle = RuleBundle(
            schemaVersion = "1.0.0",
            bundleId = "bundle-2026-04-26",
            compiledAt = "2026-04-26T08:00:00Z",
            rules = listOf(ruleWithTags("rule-mobile", listOf(MOBILE_REAL_LIFE_TAG))),
        )

        val subset = subsetForMobileRealLife(bundle)

        assertEquals("1.0.0", subset.schemaVersion)
        assertEquals("bundle-2026-04-26", subset.bundleId)
        assertEquals("2026-04-26T08:00:00Z", subset.compiledAt)
    }

    @Test
    fun subset_preserves_rule_order_so_evaluator_iteration_remains_deterministic() {
        // Bundle order matters because T214 evaluateBundle iterates
        // in order and downstream priority queues see emissions in
        // that order. The filter must not re-shuffle.
        val bundle = RuleBundle(
            schemaVersion = "1.0.0",
            bundleId = "test-bundle",
            compiledAt = "2026-04-26T08:00:00Z",
            rules = listOf(
                ruleWithTags("rule-A", listOf(MOBILE_REAL_LIFE_TAG)),
                ruleWithTags("rule-skip-1", listOf("desktop")),
                ruleWithTags("rule-B", listOf(MOBILE_REAL_LIFE_TAG)),
                ruleWithTags("rule-skip-2", listOf("desktop")),
                ruleWithTags("rule-C", listOf(MOBILE_REAL_LIFE_TAG)),
            ),
        )

        val subset = subsetForMobileRealLife(bundle)

        assertEquals(listOf("rule-A", "rule-B", "rule-C"), subset.rules.map { it.id })
    }

    @Test
    fun subset_of_empty_rules_bundle_returns_empty_rules_bundle_so_zero_rule_payload_doesnt_crash() {
        val bundle = RuleBundle(
            schemaVersion = "1.0.0",
            bundleId = "empty-bundle",
            compiledAt = "2026-04-26T08:00:00Z",
            rules = emptyList(),
        )

        val subset = subsetForMobileRealLife(bundle)

        assertTrue(subset.rules.isEmpty())
        assertEquals("empty-bundle", subset.bundleId)
    }

    @Test
    fun rule_with_applicable_to_decodes_from_snake_case_payload_so_python_desktop_format_stays_in_sync() {
        // Anchor against the Python source-of-truth wire format —
        // the server emits applicable_to as a JSON array of strings.
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
                  "insight": {"category": "braking"},
                  "applicable_to": ["mobile_real_life", "desktop"]
                }
              ]
            }
        """.trimIndent()

        val bundle = lapJson.decodeFromString<RuleBundle>(payload)

        assertEquals(listOf("mobile_real_life", "desktop"), bundle.rules.single().applicableTo)
    }

    @Test
    fun rule_with_default_empty_applicable_to_omits_field_when_encoded_so_wire_payload_stays_compact() {
        // lapJson is configured with encodeDefaults = false. A rule
        // using the default empty applicableTo must NOT serialize
        // the field — saves bytes when many rules don't carry the
        // tag (matches the min_corroborating_laps default-omit policy).
        val emptyObject: JsonObject = buildJsonObject { }
        val rule = Rule(
            id = "test.rule",
            category = "technique",
            condition = emptyObject,
            insight = emptyObject,
        )

        val encoded = lapJson.encodeToString(rule)

        assertFalse(
            "Encoded rule should not contain applicable_to when value is the default. Encoded: $encoded",
            encoded.contains("applicable_to"),
        )
    }

    @Test
    fun rule_without_applicable_to_field_decodes_with_empty_default_so_omitted_field_is_treated_as_no_tags() {
        // Forward-compat with bundles that don't carry the tag yet
        // (the existing rule library before server-side T216 lands).
        // Decodes cleanly, no exception. The strict subsetForMobileRealLife
        // filter then excludes those rules, which is the correct policy.
        val payload = """
            {
              "schema_version": "1.0.0",
              "bundle_id": "bundle-old",
              "compiled_at": "2026-04-26T08:00:00Z",
              "rules": [
                {
                  "id": "test.rule",
                  "category": "technique",
                  "condition": {"type": "feature_threshold", "feature": "x", "comparison": "gt", "value": 1.0},
                  "insight": {}
                }
              ]
            }
        """.trimIndent()

        val bundle = lapJson.decodeFromString<RuleBundle>(payload)

        assertTrue(bundle.rules.single().applicableTo.isEmpty())
    }

    private fun ruleWithTags(id: String, tags: List<String>): Rule = Rule(
        id = id,
        category = "test-category",
        condition = buildJsonObject { },
        insight = buildJsonObject { },
        applicableTo = tags,
    )
}
