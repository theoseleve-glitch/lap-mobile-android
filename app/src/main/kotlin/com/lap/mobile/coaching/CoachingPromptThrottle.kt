package com.lap.mobile.coaching

import java.time.Duration
import java.time.Instant

/**
 * Global throttle limiting how often coaching insights can be
 * surfaced to the user (T220 — max 1 per [cooldownDuration]).
 *
 * Why global, not per-rule (which is what the desktop's
 * InsightCooldownFilter does): mobile's attention budget during
 * recording is constrained — the driver can meaningfully process
 * maybe one coaching tip per 30s before they stop reading them.
 * A per-rule throttle would still let 5 different rules fire 5
 * cards in 5 seconds, which exceeds that budget. A global
 * throttle bounds the total card rate regardless of source.
 *
 * Per-rule cooldown can layer on top of this later if a specific
 * noisy rule needs further suppression beyond the global cap.
 *
 * [cooldownDuration] = `Duration.ZERO` disables the throttle —
 * `tryAdmit` always returns true. Useful for tests that don't
 * care about throttle semantics and for an "off" preference
 * setting if the user explicitly wants every tip.
 *
 * Default `30` seconds matches the desktop's
 * [InsightCooldownFilter] default — long enough that the same
 * coaching beat stops being useful (driver heard it; either
 * fixing it or actively ignoring it for now), short enough that
 * a recurring mistake can re-surface after a corner sequence.
 *
 * Thread-safety: a single [SessionCoachingHook] owns one throttle
 * and calls [tryAdmit] from one coroutine collector, so the
 * mutation isn't typically contended. The `synchronized` block is
 * cheap insurance against future call sites that fan out across
 * dispatchers (e.g., a UI-thread "show now" override path).
 */
class CoachingPromptThrottle(
    private val cooldownDuration: Duration = DEFAULT_COOLDOWN,
) {
    private var lastAdmittedAt: Instant? = null
    private val lock = Any()

    /**
     * Returns `true` and updates internal state if [now] is at
     * or past the cooldown window from the last admitted call,
     * `false` (and no state change) otherwise.
     *
     * Boundary policy is INCLUSIVE — a call at exactly
     * `lastAdmittedAt + cooldownDuration` is admitted, not
     * suppressed. Reasoning: the boundary case is rare enough
     * that the choice is mostly arbitrary, but inclusive is
     * easier to reason about ("after waiting the cooldown,
     * you're allowed to fire").
     */
    fun tryAdmit(now: Instant): Boolean = synchronized(lock) {
        val last = lastAdmittedAt
        val admitted = last == null || Duration.between(last, now) >= cooldownDuration
        if (admitted) {
            lastAdmittedAt = now
        }
        admitted
    }

    companion object {
        val DEFAULT_COOLDOWN: Duration = Duration.ofSeconds(30)
    }
}
