package com.lap.mobile.coaching

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Locks the contract for skill-level-aware body selection in
 * [parseInsightPromptPayload] (T221) — the rule's `bodies` JSON
 * object can ship per-tier explanations and the parser picks the
 * one matching the user's [UserSkillLevel].
 *
 * Coverage:
 *   - Each tier (BEGINNER / INTERMEDIATE / EXPERT) selects the
 *     matching body when all three are present
 *   - Missing user's tier falls back to INTERMEDIATE (broadest-
 *     applicable safe default)
 *   - Missing both user's tier AND intermediate falls back to
 *     any available body (so a partial bodies object still
 *     renders something)
 *   - Empty bodies object returns null (no body = unrenderable)
 *   - `bodies` (object) takes precedence over `body` (string) so
 *     skill-aware schema wins when both shipped (server in partial-
 *     rollout window may ship both for client compatibility)
 *   - Flat `body` string still works when no `bodies` (T217
 *     forward-compat — existing payloads without skill variants
 *     keep parsing)
 *   - Non-string body value within `bodies` triggers the same
 *     fallback chain (defensive against server typo emitting an
 *     array or number under a tier key)
 *   - Default skillLevel parameter = INTERMEDIATE so existing
 *     T217 callers don't break
 */
class InsightPromptPayloadVariantTest {
    @Test
    fun beginner_skill_level_selects_beginner_body_so_coaching_matches_user_expertise_tier() {
        val insight = insightWithBodies(
            beginner = "Brake earlier — try the 100m board.",
            intermediate = "Try braking 30m earlier to carry more apex speed.",
            expert = "Trail-brake from 95% to 30% over 200m past apex.",
        )

        val payload = parseInsightPromptPayload(insight, UserSkillLevel.BEGINNER)

        assertEquals("Brake earlier — try the 100m board.", payload!!.body)
    }

    @Test
    fun intermediate_skill_level_selects_intermediate_body_so_default_tier_renders_correctly() {
        // Headline T221 invariant: each user gets the explanation
        // tuned to their skill level — same rule, different body.
        val insight = insightWithBodies(
            beginner = "Brake earlier — try the 100m board.",
            intermediate = "Try braking 30m earlier to carry more apex speed.",
            expert = "Trail-brake from 95% to 30% over 200m past apex.",
        )

        val payload = parseInsightPromptPayload(insight, UserSkillLevel.INTERMEDIATE)

        assertEquals("Try braking 30m earlier to carry more apex speed.", payload!!.body)
    }

    @Test
    fun expert_skill_level_selects_expert_body_so_advanced_users_get_deep_explanation() {
        val insight = insightWithBodies(
            beginner = "Brake earlier — try the 100m board.",
            intermediate = "Try braking 30m earlier to carry more apex speed.",
            expert = "Trail-brake from 95% to 30% over 200m past apex.",
        )

        val payload = parseInsightPromptPayload(insight, UserSkillLevel.EXPERT)

        assertEquals("Trail-brake from 95% to 30% over 200m past apex.", payload!!.body)
    }

    @Test
    fun missing_users_tier_falls_back_to_intermediate_body_so_partial_bodies_object_still_renders() {
        // Headline T221 forward-compat invariant: a rule may ship
        // only intermediate (the default) without explicit beginner
        // or expert variants. Beginner / expert users get the
        // intermediate body rather than seeing nothing.
        val insight = CoachingInsight(
            ruleId = "rule-test",
            payload = buildJsonObject {
                put("title", "Title")
                putJsonObject("bodies") {
                    put("intermediate", "Default intermediate body")
                }
            },
        )

        val beginnerPayload = parseInsightPromptPayload(insight, UserSkillLevel.BEGINNER)
        val expertPayload = parseInsightPromptPayload(insight, UserSkillLevel.EXPERT)

        assertEquals("Default intermediate body", beginnerPayload!!.body)
        assertEquals("Default intermediate body", expertPayload!!.body)
    }

    @Test
    fun missing_users_tier_AND_intermediate_falls_back_to_any_available_so_all_explicit_variant_combos_render() {
        // Edge case: rule ships only beginner and expert (skipping
        // intermediate). An INTERMEDIATE user should still see
        // SOMETHING rather than a blank card. Falling back to "any
        // available body" keeps the tip useful even at this odd
        // schema shape.
        val insight = CoachingInsight(
            ruleId = "rule-test",
            payload = buildJsonObject {
                put("title", "Title")
                putJsonObject("bodies") {
                    put("beginner", "Beginner only")
                    put("expert", "Expert only")
                }
            },
        )

        val payload = parseInsightPromptPayload(insight, UserSkillLevel.INTERMEDIATE)

        // Either "Beginner only" or "Expert only" — both are valid
        // last-resort fallbacks; the test just locks "renders
        // something non-null".
        assertEquals(true, payload?.body?.isNotBlank())
    }

    @Test
    fun empty_bodies_object_returns_null_so_zero_body_payload_doesnt_render_blank_card() {
        val insight = CoachingInsight(
            ruleId = "rule-test",
            payload = buildJsonObject {
                put("title", "Title")
                putJsonObject("bodies") { }
            },
        )

        assertNull(parseInsightPromptPayload(insight, UserSkillLevel.INTERMEDIATE))
    }

    @Test
    fun bodies_object_takes_precedence_over_flat_body_string_so_richer_schema_wins_during_partial_rollout() {
        // Server in partial-rollout window may ship BOTH `body`
        // (for older clients that don't know about `bodies`) AND
        // `bodies` (for newer skill-aware clients). Newer clients
        // should pick from bodies and ignore the flat fallback.
        val insight = CoachingInsight(
            ruleId = "rule-test",
            payload = buildJsonObject {
                put("title", "Title")
                put("body", "Old flat body for legacy clients")
                putJsonObject("bodies") {
                    put("intermediate", "New tier-aware body")
                }
            },
        )

        val payload = parseInsightPromptPayload(insight, UserSkillLevel.INTERMEDIATE)

        assertEquals("New tier-aware body", payload!!.body)
    }

    @Test
    fun flat_body_string_still_used_when_no_bodies_object_so_t217_payload_format_keeps_working() {
        // Forward-compat with T217 schema (body as flat string) —
        // existing rules without skill variants must continue
        // rendering correctly regardless of skill level.
        val insight = CoachingInsight(
            ruleId = "rule-test",
            payload = buildJsonObject {
                put("title", "Title")
                put("body", "Single body for all tiers")
            },
        )

        listOf(UserSkillLevel.BEGINNER, UserSkillLevel.INTERMEDIATE, UserSkillLevel.EXPERT).forEach { tier ->
            val payload = parseInsightPromptPayload(insight, tier)
            assertEquals(
                "tier $tier should get the flat body",
                "Single body for all tiers",
                payload!!.body,
            )
        }
    }

    @Test
    fun non_string_body_for_users_tier_falls_back_so_server_typo_doesnt_drop_the_insight() {
        // Defensive: if a tier key is mapped to an array or number
        // (server bug emitting wrong shape), fall back to
        // intermediate rather than dropping the whole insight.
        val insight = CoachingInsight(
            ruleId = "rule-test",
            payload = buildJsonObject {
                put("title", "Title")
                putJsonObject("bodies") {
                    putJsonArray("beginner") { add("array instead of string") }
                    put("intermediate", "Healthy intermediate body")
                    put("expert", "Healthy expert body")
                }
            },
        )

        val payload = parseInsightPromptPayload(insight, UserSkillLevel.BEGINNER)

        assertEquals("Healthy intermediate body", payload!!.body)
    }

    @Test
    fun default_skill_level_parameter_is_intermediate_so_t217_callers_dont_break() {
        // Existing T217 callers don't pass a skillLevel argument.
        // The default must be INTERMEDIATE so they keep working
        // with single-body payloads (already tested in T217's
        // InsightPromptPayloadTest).
        val insight = CoachingInsight(
            ruleId = "rule-test",
            payload = buildJsonObject {
                put("title", "Title")
                putJsonObject("bodies") {
                    put("beginner", "B")
                    put("intermediate", "I")
                    put("expert", "E")
                }
            },
        )

        val payload = parseInsightPromptPayload(insight)

        assertEquals("I", payload!!.body)
    }

    private fun insightWithBodies(beginner: String, intermediate: String, expert: String): CoachingInsight =
        CoachingInsight(
            ruleId = "rule-test",
            payload = buildJsonObject {
                put("title", "Title")
                putJsonObject("bodies") {
                    put("beginner", beginner)
                    put("intermediate", intermediate)
                    put("expert", expert)
                }
            },
        )

    @Suppress("unused")
    private fun emptyJsonObject(): JsonObject = buildJsonObject { }
}
