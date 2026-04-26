package com.lap.mobile.coaching

import com.lap.mobile.data.SessionEndEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Locks the contract for [SessionCoachingHook] (T215) — the
 * Kotlin equivalent of the desktop's per-lap-end hook into
 * LiveProcessor wired against [com.lap.mobile.data.SessionCoordinator]'s
 * `endEvents` flow.
 *
 * Scoped to the hook's orchestration responsibility ONLY — does
 * NOT test [evaluateBundle] semantics (those are locked in
 * [RulesEngineTest]) and does NOT test [com.lap.mobile.data.SessionCoordinator]'s
 * own emit semantics (locked in its own test). What we test:
 *   - End event triggers exactly one engine evaluation
 *   - Insights list is propagated verbatim from [evaluateBundle]
 *   - Missing bundle short-circuits silently (no insights, no errors)
 *   - Malformed condition routes through [SessionCoachingHook.errors]
 *     not the collector's parent scope (so a server-side rule bug
 *     can't crash the recording foreground service)
 *   - bundleProvider + featureExtractor are called per-event so
 *     bundle hot-reload between events is picked up
 *   - Multiple end events produce ordered insights batches
 *
 * Why a [MutableSharedFlow]<[SessionEndEvent]> input rather than a
 * real SessionCoordinator: SessionCoordinator needs SessionDao +
 * CaptureOrchestrator + JournalWriter + Clock + PreflightGate to
 * construct — heavy fakes for what's a one-flow dependency. The
 * hook's contract is "subscribe a Flow", so the test exercises
 * that contract directly. Production wiring (in DI) passes
 * `coordinator.endEvents` to `attach`.
 *
 * Why [UnconfinedTestDispatcher]: makes launched coroutines run
 * eagerly, eliminating the StandardTestDispatcher race where a
 * `MutableSharedFlow.emit()` runs before the subscriber's `collect`
 * has formally subscribed. The hook's behavior is dispatcher-
 * agnostic; production runs on the foreground service's scope.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionCoachingHookTest {
    @Test
    fun end_event_with_firing_rule_emits_matching_insight_so_engine_runs_on_session_end() =
        runTest(UnconfinedTestDispatcher()) {
            val firingBundle = bundleWithRule(
                ruleId = "rule-fast-corner",
                condition = featureGt("apex_speed_mps", 80.0),
                insightCategory = "cornering",
            )
            val events = MutableSharedFlow<SessionEndEvent>(extraBufferCapacity = 4)
            val hook = SessionCoachingHook(
                bundleProvider = { firingBundle },
                featureExtractor = { mapOf("apex_speed_mps" to 95.0) },
            )
            val collected = mutableListOf<List<CoachingInsight>>()
            backgroundScope.launch { hook.insights.collect { collected += it } }

            hook.attach(backgroundScope, events)

            events.emit(makeEndEvent())

            assertEquals(1, collected.size)
            assertEquals(listOf("rule-fast-corner"), collected[0].map { it.ruleId })
        }

    @Test
    fun end_event_with_no_firing_rule_emits_empty_insights_list_so_silent_lap_is_observable() =
        runTest(UnconfinedTestDispatcher()) {
            // Headline T215 invariant: distinguish "engine ran, no rule
            // fired" from "engine didn't run". Empty-list emission tells
            // the audit writer that the lap was evaluated — important
            // for "client coaching ran clean" vs "client coaching never
            // started" diagnostics.
            val nonFiringBundle = bundleWithRule(
                ruleId = "rule-fast-corner",
                condition = featureGt("apex_speed_mps", 80.0),
                insightCategory = "cornering",
            )
            val events = MutableSharedFlow<SessionEndEvent>(extraBufferCapacity = 4)
            val hook = SessionCoachingHook(
                bundleProvider = { nonFiringBundle },
                featureExtractor = { mapOf("apex_speed_mps" to 50.0) },
            )
            val collected = mutableListOf<List<CoachingInsight>>()
            backgroundScope.launch { hook.insights.collect { collected += it } }

            hook.attach(backgroundScope, events)

            events.emit(makeEndEvent())

            assertEquals(1, collected.size)
            assertTrue(collected[0].isEmpty())
        }

    @Test
    fun end_event_with_no_bundle_emits_nothing_so_pre_bundle_session_silently_skips() =
        runTest(UnconfinedTestDispatcher()) {
            // Headline T215 invariant: cold-start (no cached bundle, no
            // server reach) means coaching is unavailable for this session.
            // Hook short-circuits silently — no insights AND no errors.
            // Distinct from "engine ran clean" (empty list emitted) — this
            // is "engine never ran", correctly signalled by absence.
            val events = MutableSharedFlow<SessionEndEvent>(extraBufferCapacity = 4)
            val hook = SessionCoachingHook(
                bundleProvider = { null },
                featureExtractor = { mapOf("apex_speed_mps" to 95.0) },
            )
            val insightsCollected = mutableListOf<List<CoachingInsight>>()
            val errorsCollected = mutableListOf<RuleBundleException.Condition>()
            backgroundScope.launch { hook.insights.collect { insightsCollected += it } }
            backgroundScope.launch { hook.errors.collect { errorsCollected += it } }

            hook.attach(backgroundScope, events)

            events.emit(makeEndEvent())

            assertTrue("expected no insights when bundle absent", insightsCollected.isEmpty())
            assertTrue("expected no errors when bundle absent", errorsCollected.isEmpty())
        }

    @Test
    fun end_event_with_malformed_condition_emits_error_not_insight_so_server_bug_doesnt_crash_hook() =
        runTest(UnconfinedTestDispatcher()) {
            // Headline T215 invariant: server-authored rule with a malformed
            // condition (unknown operator, missing required field) must
            // NOT crash the hook's collector — that would take down the
            // recording foreground service. Instead route through the
            // errors flow so T222's Sentry adoption surfaces the bug.
            val malformedBundle = bundleWithRule(
                ruleId = "rule-broken",
                condition = buildJsonObject { put("type", "between") },
                insightCategory = "cornering",
            )
            val events = MutableSharedFlow<SessionEndEvent>(extraBufferCapacity = 4)
            val hook = SessionCoachingHook(
                bundleProvider = { malformedBundle },
                featureExtractor = { mapOf("apex_speed_mps" to 95.0) },
            )
            val insightsCollected = mutableListOf<List<CoachingInsight>>()
            val errorsCollected = mutableListOf<RuleBundleException.Condition>()
            backgroundScope.launch { hook.insights.collect { insightsCollected += it } }
            backgroundScope.launch { hook.errors.collect { errorsCollected += it } }

            hook.attach(backgroundScope, events)

            events.emit(makeEndEvent())

            assertTrue("expected no insights when engine threw Condition", insightsCollected.isEmpty())
            assertEquals(1, errorsCollected.size)
            assertTrue(
                "expected error to mention the bad operator; got: ${errorsCollected[0].message}",
                errorsCollected[0].message?.contains("between") == true,
            )
        }

    @Test
    fun two_end_events_emit_two_insights_batches_so_per_event_evaluation_is_independent() =
        runTest(UnconfinedTestDispatcher()) {
            val firingBundle = bundleWithRule(
                ruleId = "rule-fast-corner",
                condition = featureGt("apex_speed_mps", 80.0),
                insightCategory = "cornering",
            )
            val events = MutableSharedFlow<SessionEndEvent>(extraBufferCapacity = 4)
            val hook = SessionCoachingHook(
                bundleProvider = { firingBundle },
                featureExtractor = { mapOf("apex_speed_mps" to 95.0) },
            )
            val collected = mutableListOf<List<CoachingInsight>>()
            backgroundScope.launch { hook.insights.collect { collected += it } }

            hook.attach(backgroundScope, events)

            events.emit(makeEndEvent(sessionId = "01HQ000000000000000000FIRST"))
            events.emit(makeEndEvent(sessionId = "01HQ000000000000000000SECND"))

            assertEquals(2, collected.size)
        }

    @Test
    fun bundle_provider_called_per_event_so_hot_reloaded_bundle_is_picked_up_on_next_session() =
        runTest(UnconfinedTestDispatcher()) {
            // Headline T215 invariant: bundle changes between events
            // (server hot-reload via T212 fetcher refreshing T213 cache)
            // must be picked up on the NEXT event without re-attaching
            // the hook. Provider is called per-event, not cached at
            // attach time.
            var bundleVersion = 0
            val bundleA = bundleWithRule(
                ruleId = "rule-A",
                condition = featureGt("speed", 50.0),
                insightCategory = "v1",
            )
            val bundleB = bundleWithRule(
                ruleId = "rule-B",
                condition = featureGt("speed", 50.0),
                insightCategory = "v2",
            )
            val events = MutableSharedFlow<SessionEndEvent>(extraBufferCapacity = 4)
            val hook = SessionCoachingHook(
                bundleProvider = {
                    bundleVersion += 1
                    if (bundleVersion == 1) bundleA else bundleB
                },
                featureExtractor = { mapOf("speed" to 60.0) },
            )
            val collected = mutableListOf<List<CoachingInsight>>()
            backgroundScope.launch { hook.insights.collect { collected += it } }

            hook.attach(backgroundScope, events)

            events.emit(makeEndEvent())
            events.emit(makeEndEvent())

            assertEquals(listOf("rule-A"), collected[0].map { it.ruleId })
            assertEquals(listOf("rule-B"), collected[1].map { it.ruleId })
        }

    @Test
    fun feature_extractor_receives_each_end_event_so_per_session_features_drive_evaluation() =
        runTest(UnconfinedTestDispatcher()) {
            val firingBundle = bundleWithRule(
                ruleId = "rule-fast-corner",
                condition = featureGt("session_id_int", 0.0),
                insightCategory = "cornering",
            )
            val events = MutableSharedFlow<SessionEndEvent>(extraBufferCapacity = 4)
            val seenEvents = mutableListOf<SessionEndEvent>()
            val hook = SessionCoachingHook(
                bundleProvider = { firingBundle },
                featureExtractor = { event ->
                    seenEvents += event
                    mapOf("session_id_int" to event.sampleCount)
                },
            )
            backgroundScope.launch { hook.insights.collect { /* drain */ } }

            hook.attach(backgroundScope, events)

            val firstEvent = makeEndEvent(sampleCount = 100)
            val secondEvent = makeEndEvent(sampleCount = 200)
            events.emit(firstEvent)
            events.emit(secondEvent)

            assertEquals(2, seenEvents.size)
            assertEquals(100, seenEvents[0].sampleCount)
            assertEquals(200, seenEvents[1].sampleCount)
        }

    private fun makeEndEvent(
        sessionId: String = "01HQNRXJ8VWPC5VKY7BQGZH7K2",
        sampleCount: Int = 1234,
    ): SessionEndEvent = SessionEndEvent(
        sessionId = sessionId,
        endedAt = Instant.parse("2026-04-26T12:34:56Z"),
        sampleCount = sampleCount,
    )

    private fun bundleWithRule(ruleId: String, condition: JsonObject, insightCategory: String): RuleBundle = RuleBundle(
        schemaVersion = "1.0.0",
        bundleId = "test-bundle",
        compiledAt = "2026-04-26T08:00:00Z",
        rules = listOf(
            Rule(
                id = ruleId,
                category = insightCategory,
                condition = condition,
                insight = buildJsonObject { put("category", insightCategory) },
            ),
        ),
    )

    private fun featureGt(feature: String, value: Double): JsonObject = buildJsonObject {
        put("type", "feature_threshold")
        put("feature", feature)
        put("comparison", "gt")
        put("value", value)
    }
}
