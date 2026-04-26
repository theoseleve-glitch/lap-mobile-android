package com.lap.mobile.coaching

import com.lap.mobile.data.SessionEndEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Wires the rules engine (T214 [evaluateBundle]) to the recording
 * lifecycle (T215 — Kotlin equivalent of the desktop's per-lap-end
 * hook into LiveProcessor).
 *
 * Per CLAUDE.md mobile has no local lap detection — server computes
 * laps from raw GPS — so the natural trigger on mobile is
 * [com.lap.mobile.data.SessionCoordinator]'s `endEvents` flow
 * (per-session-end, not per-lap). Future work that adds in-app
 * lap detection or a server-streamed lap-end feed can wire a
 * different `Flow<SessionEndEvent>` source through `attach` without
 * changing the hook's contract.
 *
 * Two output flows:
 *   - [insights]: emitted on every successful evaluation (the happy
 *     path — list may be empty when no rule fired). T217's
 *     bottom-sheet prompt and T215's coaching_insights.json audit
 *     writer subscribe here.
 *   - [errors]: emitted only when [evaluateBundle] threw a
 *     [RuleBundleException.Condition]. That's the desktop's "fail
 *     loudly on malformed rule DSL" intent re-routed through a
 *     non-crashing channel — T222's Sentry adoption will subscribe
 *     this directly.
 *
 * Exceptions OTHER than [RuleBundleException.Condition] propagate
 * up the collector's scope. Those are real bugs (NPE,
 * IllegalStateException, etc.) — masking them here would let
 * regressions hide in plain sight.
 */
class SessionCoachingHook(
    private val bundleProvider: suspend () -> RuleBundle?,
    private val featureExtractor: suspend (SessionEndEvent) -> SessionFeatures,
) {
    private val _insights: MutableSharedFlow<List<CoachingInsight>> =
        MutableSharedFlow(extraBufferCapacity = INSIGHTS_BUFFER)
    val insights: SharedFlow<List<CoachingInsight>> = _insights.asSharedFlow()

    private val _errors: MutableSharedFlow<RuleBundleException.Condition> =
        MutableSharedFlow(extraBufferCapacity = ERRORS_BUFFER)
    val errors: SharedFlow<RuleBundleException.Condition> = _errors.asSharedFlow()

    /**
     * Subscribe [events] and run the rules engine on every emission.
     * Returns the launched [Job] so callers can cancel the hook
     * (typically tied to the recording session lifecycle).
     */
    fun attach(scope: CoroutineScope, events: Flow<SessionEndEvent>): Job = scope.launch {
        events.collect { event ->
            val bundle = bundleProvider() ?: return@collect
            val features = featureExtractor(event)
            try {
                _insights.tryEmit(evaluateBundle(bundle, features))
            } catch (e: RuleBundleException.Condition) {
                _errors.tryEmit(e)
            }
        }
    }

    private companion object {
        const val INSIGHTS_BUFFER: Int = 8
        const val ERRORS_BUFFER: Int = 8
    }
}
