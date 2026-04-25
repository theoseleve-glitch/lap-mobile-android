package com.lap.mobile.capture

/**
 * Block E entry point (T086): the full preflight surface that
 * aggregates every individual readiness check (T087+ location, T088
 * notification, T089 bluetooth, T090 track-selected, …) into a single
 * [PreflightStatus]. Extends [PreflightGate] so the
 * [com.lap.mobile.data.SessionCoordinator] can keep depending on the
 * narrower interface (T077) — no coordinator changes when a new check
 * is added; just register another [PreflightCheck] in the service's
 * list.
 *
 * The interface stays bare (`gate.check()` is the only contract) so a
 * future variant (e.g. a per-check stream for the readiness UI in
 * T097) can extend in either direction without breaking
 * [com.lap.mobile.data.SessionCoordinator].
 */
interface PreflightService : PreflightGate

/**
 * Default implementation: walks each [PreflightCheck] in the supplied
 * [checks] list, collects every non-null `failureReason`, and returns
 * [PreflightStatus.Ready] iff none failed. Aggregating *all* failures
 * (rather than short-circuiting on the first) lets the UI (T097)
 * present every blocker at once instead of trickling them out one
 * remediation at a time.
 *
 * Order is preserved so the UI can present reasons in a deterministic
 * order driven by the order they were registered in the DI module.
 */
class DefaultPreflightService(
    private val checks: List<PreflightCheck>,
) : PreflightService {
    override suspend fun check(): PreflightStatus {
        val reasons = mutableListOf<String>()
        for (singleCheck in checks) {
            val reason = singleCheck.failureReason()
            if (reason != null) reasons.add(reason)
        }
        return if (reasons.isEmpty()) PreflightStatus.Ready else PreflightStatus.NotReady(reasons.toList())
    }
}
