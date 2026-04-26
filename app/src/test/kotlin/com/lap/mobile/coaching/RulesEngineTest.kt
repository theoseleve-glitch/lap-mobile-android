package com.lap.mobile.coaching

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Locks the contract for [evaluateCondition] and [evaluateBundle]
 * (T214) — the Kotlin port of the desktop's
 * `datalogger.coaching.rules_engine.evaluate_*` functions.
 *
 * Coverage matrix:
 *   - Every comparison operator (lt/lte/gt/gte/eq) with above /
 *     below / boundary values
 *   - Missing-feature dormancy (returns false rather than throw)
 *   - Boolean-feature dormancy (Kotlin's `is Number` excludes
 *     Boolean — defensive against true==1 false-positives)
 *   - Recursive and/or composition with nested children
 *   - Vacuous-true `and []` / vacuous-false `or []`
 *   - Every malformed-condition path throws [RuleBundleException.Condition]
 *     (server bug must surface, not silent no-fire)
 *   - [evaluateBundle] preserves bundle order and pairs ruleId
 *     with verbatim insight payload
 *
 * Why this many tests for a "simple" port: the rules engine is
 * the live coaching brain — a missed false (silent gap) or a
 * spurious true (false coaching) both degrade trust in the
 * product. Locking each branch with a named test makes
 * regressions impossible to land without flipping a clearly-
 * named assertion.
 */
class RulesEngineTest {
    @Test
    fun feature_threshold_gt_fires_when_value_strictly_exceeds_threshold_so_above_target_triggers_coaching() {
        val condition = featureThreshold("top_speed_mps", "gt", 60.0)
        assertTrue(evaluateCondition(condition, mapOf("top_speed_mps" to 65.0)))
    }

    @Test
    fun feature_threshold_gt_does_not_fire_when_value_equals_threshold_so_strict_inequality_holds() {
        val condition = featureThreshold("top_speed_mps", "gt", 60.0)
        assertFalse(evaluateCondition(condition, mapOf("top_speed_mps" to 60.0)))
    }

    @Test
    fun feature_threshold_gte_fires_at_exact_threshold_so_inclusive_comparator_holds() {
        val condition = featureThreshold("apex_speed_mps", "gte", 80.0)
        assertTrue(evaluateCondition(condition, mapOf("apex_speed_mps" to 80.0)))
    }

    @Test
    fun feature_threshold_lt_fires_when_value_is_below_threshold_so_under_target_triggers_coaching() {
        val condition = featureThreshold("apex_speed_mps", "lt", 80.0)
        assertTrue(evaluateCondition(condition, mapOf("apex_speed_mps" to 70.0)))
    }

    @Test
    fun feature_threshold_lte_fires_at_exact_threshold_so_inclusive_under_holds() {
        val condition = featureThreshold("brake_pressure_bar", "lte", 50.0)
        assertTrue(evaluateCondition(condition, mapOf("brake_pressure_bar" to 50.0)))
    }

    @Test
    fun feature_threshold_eq_fires_only_on_exact_match_so_categorical_features_can_be_pinned() {
        val condition = featureThreshold("gear", "eq", 3.0)
        assertTrue(evaluateCondition(condition, mapOf("gear" to 3)))
        assertFalse(evaluateCondition(condition, mapOf("gear" to 4)))
    }

    @Test
    fun feature_threshold_returns_false_when_feature_missing_so_dormant_rule_doesnt_crash_engine() {
        // Headline T214 invariant: rule depending on a metric not
        // computed yet for this lap should sit dormant, not throw.
        // Distinct from a malformed condition (which IS a server bug
        // and must escalate via RuleBundleException.Condition).
        val condition = featureThreshold("apex_speed_mps", "gt", 80.0)
        assertFalse(evaluateCondition(condition, mapOf("other_feature" to 100.0)))
    }

    @Test
    fun feature_threshold_returns_false_when_feature_value_is_string_so_non_numeric_doesnt_throw() {
        val condition = featureThreshold("apex_speed_mps", "gt", 80.0)
        assertFalse(evaluateCondition(condition, mapOf("apex_speed_mps" to "fast")))
    }

    @Test
    fun feature_threshold_returns_false_when_feature_value_is_boolean_so_true_doesnt_collide_with_one() {
        // Headline T214 defense: Python's `True == 1` would fire
        // any `gt 0.5` rule against a Bool flag. Kotlin's Boolean
        // is not a Number, so `as? Number` rejects it cleanly.
        val condition = featureThreshold("is_overtaking", "gt", 0.5)
        assertFalse(evaluateCondition(condition, mapOf("is_overtaking" to true)))
    }

    @Test
    fun feature_threshold_accepts_int_value_and_coerces_for_comparison_so_int_features_work() {
        val condition = featureThreshold("lap_count", "gte", 5.0)
        assertTrue(evaluateCondition(condition, mapOf("lap_count" to 7)))
    }

    @Test
    fun feature_threshold_with_unsupported_comparison_throws_condition_so_typo_in_dsl_surfaces_loudly() {
        val condition = featureThreshold("apex_speed_mps", "neq", 80.0)
        try {
            evaluateCondition(condition, mapOf("apex_speed_mps" to 100.0))
            fail("Expected RuleBundleException.Condition for unsupported comparison")
        } catch (expected: RuleBundleException.Condition) {
            assertTrue(
                "expected message to mention the bad comparison; got: ${expected.message}",
                expected.message?.contains("neq") == true,
            )
        }
    }

    @Test
    fun feature_threshold_missing_feature_field_throws_condition_so_malformed_dsl_surfaces() {
        val condition = buildJsonObject {
            put("type", "feature_threshold")
            put("comparison", "gt")
            put("value", 80.0)
        }
        assertThrowsCondition { evaluateCondition(condition, mapOf("apex_speed_mps" to 100.0)) }
    }

    @Test
    fun feature_threshold_missing_comparison_field_throws_condition_so_malformed_dsl_surfaces() {
        val condition = buildJsonObject {
            put("type", "feature_threshold")
            put("feature", "apex_speed_mps")
            put("value", 80.0)
        }
        assertThrowsCondition { evaluateCondition(condition, mapOf("apex_speed_mps" to 100.0)) }
    }

    @Test
    fun feature_threshold_missing_value_field_throws_condition_so_malformed_dsl_surfaces() {
        val condition = buildJsonObject {
            put("type", "feature_threshold")
            put("feature", "apex_speed_mps")
            put("comparison", "gt")
        }
        assertThrowsCondition { evaluateCondition(condition, mapOf("apex_speed_mps" to 100.0)) }
    }

    @Test
    fun and_with_all_true_children_returns_true_so_logical_and_composes_correctly() {
        val condition = andOf(
            featureThreshold("speed", "gt", 50.0),
            featureThreshold("rpm", "gt", 5000.0),
        )
        assertTrue(evaluateCondition(condition, mapOf("speed" to 60.0, "rpm" to 6000.0)))
    }

    @Test
    fun and_with_one_false_child_returns_false_so_short_circuit_logic_holds() {
        val condition = andOf(
            featureThreshold("speed", "gt", 50.0),
            featureThreshold("rpm", "gt", 5000.0),
        )
        assertFalse(evaluateCondition(condition, mapOf("speed" to 60.0, "rpm" to 4000.0)))
    }

    @Test
    fun and_with_empty_children_returns_true_vacuously_so_no_constraints_means_pass() {
        // Matches Kotlin's all([]) == true, mirrors Python's all([]) == True.
        // A rule with `and: []` is degenerate but should not crash.
        val condition = buildJsonObject {
            put("type", "and")
            putJsonArray("conditions") { }
        }
        assertTrue(evaluateCondition(condition, emptyMap()))
    }

    @Test
    fun or_with_all_false_children_returns_false_so_logical_or_requires_at_least_one_true() {
        val condition = orOf(
            featureThreshold("speed", "gt", 100.0),
            featureThreshold("rpm", "gt", 9000.0),
        )
        assertFalse(evaluateCondition(condition, mapOf("speed" to 50.0, "rpm" to 4000.0)))
    }

    @Test
    fun or_with_one_true_child_returns_true_so_any_match_fires() {
        val condition = orOf(
            featureThreshold("speed", "gt", 100.0),
            featureThreshold("rpm", "gt", 5000.0),
        )
        assertTrue(evaluateCondition(condition, mapOf("speed" to 50.0, "rpm" to 6000.0)))
    }

    @Test
    fun or_with_empty_children_returns_false_vacuously_so_no_options_means_fail() {
        // Matches Kotlin's any([]) == false, mirrors Python's any([]) == False.
        val condition = buildJsonObject {
            put("type", "or")
            putJsonArray("conditions") { }
        }
        assertFalse(evaluateCondition(condition, emptyMap()))
    }

    @Test
    fun nested_and_of_or_composes_recursively_so_recursive_dsl_evaluates_correctly() {
        // Headline T214 invariant: condition is a recursive DSL,
        // so the evaluator must recurse to arbitrary depth.
        // (apex_speed > 80 OR brake_pressure > 50) AND lap_count >= 3
        val condition = andOf(
            orOf(
                featureThreshold("apex_speed_mps", "gt", 80.0),
                featureThreshold("brake_pressure_bar", "gt", 50.0),
            ),
            featureThreshold("lap_count", "gte", 3.0),
        )
        // apex too low + brake high + 5 laps → true
        assertTrue(
            evaluateCondition(
                condition,
                mapOf(
                    "apex_speed_mps" to 70.0,
                    "brake_pressure_bar" to 60.0,
                    "lap_count" to 5,
                ),
            ),
        )
        // apex too low + brake too low + 5 laps → false (or fails)
        assertFalse(
            evaluateCondition(
                condition,
                mapOf(
                    "apex_speed_mps" to 70.0,
                    "brake_pressure_bar" to 30.0,
                    "lap_count" to 5,
                ),
            ),
        )
        // both branches true but only 2 laps → false (and fails)
        assertFalse(
            evaluateCondition(
                condition,
                mapOf(
                    "apex_speed_mps" to 90.0,
                    "brake_pressure_bar" to 60.0,
                    "lap_count" to 2,
                ),
            ),
        )
    }

    @Test
    fun unknown_condition_type_throws_condition_so_server_authored_bug_surfaces_in_logs() {
        // Headline T214 invariant: server may ship rules using an
        // operator the client doesn't know yet (deploy skew, version
        // gap). Silently no-firing would create invisible coaching
        // gaps; throw forces visibility via Sentry/logs.
        val condition = buildJsonObject {
            put("type", "between")
            put("feature", "apex_speed_mps")
        }
        try {
            evaluateCondition(condition, mapOf("apex_speed_mps" to 100.0))
            fail("Expected RuleBundleException.Condition for unknown operator")
        } catch (expected: RuleBundleException.Condition) {
            assertTrue(
                "expected message to mention the unknown operator; got: ${expected.message}",
                expected.message?.contains("between") == true,
            )
        }
    }

    @Test
    fun missing_type_field_throws_condition_so_malformed_root_dsl_surfaces() {
        val condition = buildJsonObject { put("feature", "apex_speed_mps") }
        assertThrowsCondition { evaluateCondition(condition, emptyMap()) }
    }

    @Test
    fun and_with_non_array_conditions_field_throws_condition_so_malformed_compositor_surfaces() {
        val condition = buildJsonObject {
            put("type", "and")
            put("conditions", "not-an-array")
        }
        assertThrowsCondition { evaluateCondition(condition, emptyMap()) }
    }

    @Test
    fun evaluate_bundle_returns_insight_for_each_firing_rule_in_bundle_order() {
        // Headline T214 invariant: bundle order preserved so
        // downstream priority queues see a deterministic stream.
        // Insight payload pulled verbatim from rule.insight.
        val bundle = RuleBundle(
            schemaVersion = "1.0.0",
            bundleId = "test-bundle",
            compiledAt = "2026-04-26T08:00:00Z",
            rules = listOf(
                Rule(
                    id = "rule-fast-apex",
                    category = "cornering",
                    condition = featureThreshold("apex_speed_mps", "gt", 80.0),
                    insight = buildJsonObject { put("category", "apex") },
                ),
                Rule(
                    id = "rule-light-braking",
                    category = "braking",
                    condition = featureThreshold("brake_pressure_bar", "lt", 30.0),
                    insight = buildJsonObject { put("category", "braking") },
                ),
                Rule(
                    id = "rule-redline",
                    category = "shifting",
                    condition = featureThreshold("rpm", "gt", 9000.0),
                    insight = buildJsonObject { put("category", "shifting") },
                ),
            ),
        )

        // First and third fire; second does not.
        val features = mapOf(
            "apex_speed_mps" to 90.0,
            "brake_pressure_bar" to 50.0,
            "rpm" to 9500.0,
        )
        val insights = evaluateBundle(bundle, features)

        assertEquals(2, insights.size)
        assertEquals("rule-fast-apex", insights[0].ruleId)
        assertEquals("rule-redline", insights[1].ruleId)
        assertEquals(
            buildJsonObject { put("category", "apex") },
            insights[0].payload,
        )
    }

    @Test
    fun evaluate_bundle_returns_empty_list_when_no_rule_fires_so_quiet_lap_is_branchless() {
        val bundle = RuleBundle(
            schemaVersion = "1.0.0",
            bundleId = "test-bundle",
            compiledAt = "2026-04-26T08:00:00Z",
            rules = listOf(
                Rule(
                    id = "rule-fast-apex",
                    category = "cornering",
                    condition = featureThreshold("apex_speed_mps", "gt", 80.0),
                    insight = buildJsonObject { },
                ),
            ),
        )

        val insights = evaluateBundle(bundle, mapOf("apex_speed_mps" to 50.0))

        assertTrue(insights.isEmpty())
    }

    @Test
    fun evaluate_bundle_with_empty_rules_returns_empty_list_so_unloaded_bundle_doesnt_crash() {
        val bundle = RuleBundle(
            schemaVersion = "1.0.0",
            bundleId = "empty-bundle",
            compiledAt = "2026-04-26T08:00:00Z",
            rules = emptyList(),
        )

        val insights = evaluateBundle(bundle, mapOf("apex_speed_mps" to 50.0))

        assertTrue(insights.isEmpty())
    }

    private fun featureThreshold(feature: String, comparison: String, value: Double): JsonObject = buildJsonObject {
        put("type", "feature_threshold")
        put("feature", feature)
        put("comparison", comparison)
        put("value", value)
    }

    private fun andOf(vararg children: JsonObject): JsonObject = buildJsonObject {
        put("type", "and")
        putJsonArray("conditions") {
            children.forEach { add(it) }
        }
    }

    private fun orOf(vararg children: JsonObject): JsonObject = buildJsonObject {
        put("type", "or")
        putJsonArray("conditions") {
            children.forEach { add(it) }
        }
    }

    private fun assertThrowsCondition(block: () -> Unit) {
        try {
            block()
            fail("Expected RuleBundleException.Condition, no exception thrown")
        } catch (expected: RuleBundleException.Condition) {
            // expected
        }
    }
}
