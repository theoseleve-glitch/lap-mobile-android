package com.lap.mobile.capture

/**
 * STUB — T086 placeholder. Each individual readiness check (T087+
 * location, T088 notification, T089 bluetooth, T090 track-selected,
 * …) implements this and the [PreflightService] aggregates them.
 *
 * `failureReason` returns `null` when the check passes, or an
 * open-vocabulary string keyed to the failing check
 * (`location_permission`, `gps_accuracy`, `bluetooth`, …) — the
 * vocabulary is shared with [PreflightStatus.NotReady.reasons] so
 * the UI (T097) can route each reason to a specific remediation
 * card.
 */
fun interface PreflightCheck {
    suspend fun failureReason(): String?
}
