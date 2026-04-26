package com.lap.mobile.core

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default crash-free-users SLO target — `≥98%` per the T237 sprint
 * spec, with a `≥100 sessions` minimum-sample-size guard so a tiny
 * cohort's noisy rate doesn't trip the gate. The 100-session floor
 * is the industry default below which crash-free-users is generally
 * considered noise rather than signal.
 */
const val DEFAULT_CRASH_FREE_USERS_TARGET_RATE: Double = 0.98
const val DEFAULT_CRASH_FREE_USERS_MIN_SAMPLE_SIZE: Int = 100

/**
 * SLO target for the crash-free-users metric (T237).
 *
 * Two knobs because the gate has two concerns:
 *  - [threshold] — the rate (in `[0.0, 1.0]`) at or above which
 *    the build is considered shippable.
 *  - [minimumSampleSize] — the smallest session count below which
 *    the rate is treated as noise (`InsufficientSampleSize` outcome)
 *    regardless of value. Critical because a 100% rate over 5
 *    sessions and an 80% rate over 5 sessions are both meaningless.
 *
 * Defaulted constants live at file scope so a future Hilt provider
 * can override one without re-deriving the other.
 */
data class CrashFreeUsersTarget(
    val threshold: Double = DEFAULT_CRASH_FREE_USERS_TARGET_RATE,
    val minimumSampleSize: Int = DEFAULT_CRASH_FREE_USERS_MIN_SAMPLE_SIZE,
)

/**
 * UI / release-pipeline-facing outcome of evaluating a measured
 * crash-free-users rate against the [CrashFreeUsersTarget].
 *
 * Three variants because each maps to a different release action:
 *  - [Meets] — proceed with ship.
 *  - [BelowTarget] — block ship / file incident / page oncall (the
 *    specific action is a release-process policy decision, not the
 *    gate's responsibility).
 *  - [InsufficientSampleSize] — the gate cannot trustworthily
 *    evaluate; treat as "no signal yet" rather than pass or fail.
 *    Distinct from [Meets] specifically so a brand-new release with
 *    one passing user doesn't get rubber-stamped.
 */
enum class CrashFreeUsersOutcome {
    Meets,
    BelowTarget,
    InsufficientSampleSize,
}

/**
 * Pure-Kotlin gate evaluating a measured rate + sample size against
 * a [CrashFreeUsersTarget]. JVM-testable; no Sentry import.
 *
 * The sample-size guard runs FIRST: an insufficient sample short-
 * circuits to [CrashFreeUsersOutcome.InsufficientSampleSize] before
 * the threshold comparison, so a 100%-over-5-users measurement
 * doesn't accidentally rubber-stamp a release that's actually noise.
 */
@Singleton
class CrashFreeUsersGate
@Inject
constructor(
    private val target: CrashFreeUsersTarget = CrashFreeUsersTarget(),
) {
    fun evaluate(crashFreeRate: Double, sampleSize: Int): CrashFreeUsersOutcome {
        if (sampleSize < target.minimumSampleSize) return CrashFreeUsersOutcome.InsufficientSampleSize
        return if (crashFreeRate >= target.threshold) {
            CrashFreeUsersOutcome.Meets
        } else {
            CrashFreeUsersOutcome.BelowTarget
        }
    }
}

/**
 * Measured snapshot of the crash-free-users metric for a given
 * release / time window. Held opaque so the use case + gate don't
 * have to know whether the rate came from Sentry's `crashFreeRate`,
 * Crashlytics' `crashlyticsCrashFreeUsers`, or some other backend.
 *
 * @property rate the crash-free fraction in `[0.0, 1.0]`.
 * @property sampleSize the count of distinct user-sessions the rate
 * was computed over.
 */
data class CrashFreeUsersMetric(
    val rate: Double,
    val sampleSize: Int,
)

/**
 * Seam for fetching the current [CrashFreeUsersMetric] from whatever
 * upstream owns the data — Sentry's organization stats API in
 * production. Returns `null` when no metric is available (pre-launch,
 * Sentry outage, version not yet seen).
 *
 * Production HTTP impl is `[PENDING: real-users-required]` — the
 * Sentry dashboard only has data once real users produce crash
 * events; until then the impl can return `null` safely and the use
 * case wraps as [CrashFreeUsersBaselineOutcome.CannotMeasure].
 */
fun interface CrashFreeUsersAdapter {
    suspend fun fetchCurrentMetric(): CrashFreeUsersMetric?
}

/**
 * UI / release-pipeline-facing outcome of asking "is the current
 * crash-free-users baseline meeting target". Mirrors the gate's
 * outcome but adds [CannotMeasure] for the upstream-no-data case.
 */
enum class CrashFreeUsersBaselineOutcome {
    /** Adapter returned a metric; gate said it meets target. */
    MeetsTarget,

    /** Adapter returned a metric; gate said it's below target. */
    BelowTarget,

    /** Adapter returned a metric; gate said the sample is too small to trust. */
    InsufficientSampleSize,

    /** Adapter returned `null` — no metric available (pre-launch, Sentry outage, version unseen). */
    CannotMeasure,
}

/**
 * Orchestrates the T237 baseline measurement: ask the adapter for
 * the current metric, hand to the gate, wrap the gate's outcome
 * (or surface `CannotMeasure` when the adapter has no data).
 *
 * The use case never modifies the metric before handing to the gate
 * — the kdoc test [com.lap.mobile.core.CrashFreeUsersGateTest.rate_and_sample_size_pass_through_from_adapter_to_gate_byte_for_byte]
 * locks this so a future "let's normalize to a 7-day window"
 * refactor breaks here before silently changing the gate's input.
 */
@Singleton
class CrashFreeUsersBaselineUseCase
@Inject
constructor(
    private val adapter: CrashFreeUsersAdapter,
    private val gate: CrashFreeUsersGate,
) {
    suspend fun measure(): CrashFreeUsersBaselineOutcome {
        val metric = adapter.fetchCurrentMetric() ?: return CrashFreeUsersBaselineOutcome.CannotMeasure
        return when (gate.evaluate(metric.rate, metric.sampleSize)) {
            CrashFreeUsersOutcome.Meets -> CrashFreeUsersBaselineOutcome.MeetsTarget
            CrashFreeUsersOutcome.BelowTarget -> CrashFreeUsersBaselineOutcome.BelowTarget
            CrashFreeUsersOutcome.InsufficientSampleSize -> CrashFreeUsersBaselineOutcome.InsufficientSampleSize
        }
    }
}
