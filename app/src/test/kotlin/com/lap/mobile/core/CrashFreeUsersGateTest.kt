package com.lap.mobile.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val TARGET_RATE: Double = 0.98
private const val MIN_SAMPLE_SIZE: Int = 100

/**
 * Locks the T237 crash-free-users gate kernel. The original sprint
 * task is `crash-free-users target ≥98% ⇒ baseline measured`. The
 * "≥98% target" half is policy that lives in code (this kernel); the
 * "baseline measured" half is genuinely real-users-required and stays
 * `[PENDING: real-users-required]` for the instrumented surface.
 *
 * Why a dedicated gate kernel rather than a hard-coded threshold check:
 *  - The threshold (≥98%) is an SLO. Encoding it as a JVM-tested
 *    policy locks the contract for what "meets target" means under
 *    edge cases (rate exactly at boundary, rate below but with
 *    insufficient sample, zero sample size).
 *  - The minimum-sample-size guard is the bit that's NOT obvious
 *    from the spec text — crash-free-users is meaningless with very
 *    few users (one early crash on 5 users = 80% rate, doesn't mean
 *    the app is broken). Encoding the guard prevents a future
 *    "baseline measured 80% ⇒ ship blocked" panic over noise.
 *
 * What this kernel deliberately does NOT do (`[PENDING: real-users-
 * required]` for the instrumented half):
 *  - Reach into the Sentry SDK to actually fetch the dashboard
 *    crash-free-users metric — that needs a `CrashFreeUsersAdapter`
 *    HTTP impl polling Sentry's organization stats API + auth, which
 *    only matters once real users are producing data.
 *  - Decide WHAT to do when the gate returns BelowTarget (block ship?
 *    file an incident? notify oncall?) — that's a release-process
 *    policy decision separate from the technical evaluation.
 *  - Compute the metric over a specific time window (rolling 7 days
 *    is the Sentry default; could be configurable). The gate
 *    evaluates whatever rate + sample-size pair you hand it.
 */
class CrashFreeUsersGateTest {
    private val target: CrashFreeUsersTarget =
        CrashFreeUsersTarget(threshold = TARGET_RATE, minimumSampleSize = MIN_SAMPLE_SIZE)
    private val gate: CrashFreeUsersGate = CrashFreeUsersGate(target)

    // -------------------------------------------------------------
    // Threshold semantics
    // -------------------------------------------------------------

    @Test
    fun rate_well_above_target_with_sufficient_sample_meets_target() {
        val outcome = gate.evaluate(crashFreeRate = 0.995, sampleSize = 1_000)
        assertEquals(CrashFreeUsersOutcome.Meets, outcome)
    }

    @Test
    fun rate_exactly_at_target_with_sufficient_sample_meets_target_inclusive_boundary() {
        // The contract is `≥98%` so exactly 98.000% is a pass. A
        // future refactor that flipped > to >= here would silently
        // fail every "exactly at target" measurement.
        val outcome = gate.evaluate(crashFreeRate = TARGET_RATE, sampleSize = 1_000)
        assertEquals(CrashFreeUsersOutcome.Meets, outcome)
    }

    @Test
    fun rate_just_below_target_with_sufficient_sample_returns_below_target() {
        // 97.999% — within float-round-trip precision below 98% — must
        // be flagged. The release pipeline that consumes this gate
        // can decide what "below" means (block ship vs. warn).
        val outcome = gate.evaluate(crashFreeRate = 0.97999, sampleSize = 1_000)
        assertEquals(CrashFreeUsersOutcome.BelowTarget, outcome)
    }

    @Test
    fun rate_well_below_target_with_sufficient_sample_returns_below_target() {
        val outcome = gate.evaluate(crashFreeRate = 0.85, sampleSize = 1_000)
        assertEquals(CrashFreeUsersOutcome.BelowTarget, outcome)
    }

    // -------------------------------------------------------------
    // Sample-size guard semantics
    // -------------------------------------------------------------

    @Test
    fun sample_size_below_minimum_returns_insufficient_regardless_of_a_passing_rate() {
        // A 100% rate over 5 users is still meaningless; the gate
        // surfaces the noise rather than rubber-stamping a ship.
        val outcome = gate.evaluate(crashFreeRate = 1.0, sampleSize = 5)
        assertEquals(CrashFreeUsersOutcome.InsufficientSampleSize, outcome)
    }

    @Test
    fun sample_size_below_minimum_returns_insufficient_regardless_of_a_failing_rate() {
        // Symmetric to the passing-rate case — the headline insight
        // is that the gate's first job is "is this signal trustworthy",
        // not "does this number meet the bar". An early-crash 80%
        // rate over 5 users would otherwise panic the release process.
        val outcome = gate.evaluate(crashFreeRate = 0.8, sampleSize = 5)
        assertEquals(CrashFreeUsersOutcome.InsufficientSampleSize, outcome)
    }

    @Test
    fun zero_sample_size_returns_insufficient() {
        // Pre-launch / brand-new release case — Sentry returns 0
        // session count for the version. The gate must not divide
        // by zero anywhere AND must not crash; explicit
        // InsufficientSampleSize is the right answer.
        val outcome = gate.evaluate(crashFreeRate = 0.0, sampleSize = 0)
        assertEquals(CrashFreeUsersOutcome.InsufficientSampleSize, outcome)
    }

    @Test
    fun sample_size_exactly_at_minimum_evaluates_against_rate_normally_inclusive_boundary() {
        // Boundary: at exactly the documented minimum, the gate
        // proceeds to evaluate the rate. Below-by-one (99) should
        // still be Insufficient; this test locks the >= boundary.
        val passingAtMin = gate.evaluate(crashFreeRate = 0.99, sampleSize = MIN_SAMPLE_SIZE)
        val failingAtMin = gate.evaluate(crashFreeRate = 0.5, sampleSize = MIN_SAMPLE_SIZE)
        assertEquals(CrashFreeUsersOutcome.Meets, passingAtMin)
        assertEquals(CrashFreeUsersOutcome.BelowTarget, failingAtMin)
    }

    @Test
    fun sample_size_one_below_minimum_returns_insufficient_locking_the_boundary() {
        val outcome = gate.evaluate(crashFreeRate = 0.99, sampleSize = MIN_SAMPLE_SIZE - 1)
        assertEquals(CrashFreeUsersOutcome.InsufficientSampleSize, outcome)
    }

    // -------------------------------------------------------------
    // Use case orchestration (adapter + gate)
    // -------------------------------------------------------------

    @Test
    fun baseline_use_case_returns_cannot_measure_when_adapter_returns_null() = runTest {
        // Adapter signals "no metric available" — common pre-launch,
        // also during Sentry outages. The use case wraps as
        // CannotMeasure rather than letting null bubble up so the
        // caller's `when` stays exhaustive. Explicitly distinct from
        // InsufficientSampleSize (which means data exists but is too
        // sparse to evaluate).
        val adapter = StubAdapter(metric = null)
        val useCase = CrashFreeUsersBaselineUseCase(adapter, gate)

        val outcome = useCase.measure()

        assertEquals(CrashFreeUsersBaselineOutcome.CannotMeasure, outcome)
    }

    @Test
    fun baseline_use_case_wraps_meets_outcome_when_adapter_returns_passing_metric() = runTest {
        val adapter = StubAdapter(metric = CrashFreeUsersMetric(rate = 0.99, sampleSize = 1_000))
        val useCase = CrashFreeUsersBaselineUseCase(adapter, gate)

        val outcome = useCase.measure()

        assertEquals(CrashFreeUsersBaselineOutcome.MeetsTarget, outcome)
    }

    @Test
    fun baseline_use_case_wraps_below_target_outcome_when_adapter_returns_failing_metric() = runTest {
        val adapter = StubAdapter(metric = CrashFreeUsersMetric(rate = 0.95, sampleSize = 1_000))
        val useCase = CrashFreeUsersBaselineUseCase(adapter, gate)

        val outcome = useCase.measure()

        assertEquals(CrashFreeUsersBaselineOutcome.BelowTarget, outcome)
    }

    @Test
    fun baseline_use_case_wraps_insufficient_outcome_when_adapter_returns_sparse_metric() = runTest {
        val adapter = StubAdapter(metric = CrashFreeUsersMetric(rate = 1.0, sampleSize = 7))
        val useCase = CrashFreeUsersBaselineUseCase(adapter, gate)

        val outcome = useCase.measure()

        assertEquals(CrashFreeUsersBaselineOutcome.InsufficientSampleSize, outcome)
    }

    @Test
    fun rate_and_sample_size_pass_through_from_adapter_to_gate_byte_for_byte() = runTest {
        // Locks the contract that the use case doesn't rescale,
        // re-window, or otherwise re-interpret the adapter's metric
        // before handing to the gate. A future "let's normalize the
        // rate to a 7-day rolling window" refactor would break this
        // test before silently changing the gate's input.
        val adapter = RecordingAdapter(metric = CrashFreeUsersMetric(rate = 0.9876, sampleSize = 1_234))
        val useCase = CrashFreeUsersBaselineUseCase(adapter, gate)

        useCase.measure()

        assertEquals(0.9876, adapter.lastFetchedMetric?.rate ?: -1.0, 0.0)
        assertEquals(1_234, adapter.lastFetchedMetric?.sampleSize ?: -1)
    }
}

private class StubAdapter(private val metric: CrashFreeUsersMetric?) : CrashFreeUsersAdapter {
    override suspend fun fetchCurrentMetric(): CrashFreeUsersMetric? = metric
}

/**
 * Records the last metric the adapter handed back, so the test can
 * assert the use case didn't perturb it before passing to the gate.
 */
private class RecordingAdapter(private val metric: CrashFreeUsersMetric) : CrashFreeUsersAdapter {
    var lastFetchedMetric: CrashFreeUsersMetric? = null
        private set

    override suspend fun fetchCurrentMetric(): CrashFreeUsersMetric {
        lastFetchedMetric = metric
        return metric
    }
}
