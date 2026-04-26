package com.lap.mobile.core

/**
 * App-wide product-analytics reporter abstraction (T223).
 *
 * Wraps the PostHog SDK's static facade so the rest of the codebase
 * never imports `com.posthog.*` directly — three benefits mirror
 * the [SentryReporter] (T222) rationale:
 *  1. Tests inject [NoOpAnalyticsReporter] (or a recording fake)
 *     without touching the SDK's static state.
 *  2. T224 telemetry-opt-out swaps the binding to NoOp without
 *     editing call sites.
 *  3. Future replacement of PostHog with another product-analytics
 *     backend changes only [PostHogAnalyticsReporter] not the
 *     dozens of call sites (LapApplication, SessionCoordinator,
 *     UploadBundleWorker, future ViewModels).
 *
 * Single-method shape — `track(event, properties)` — matches
 * PostHog's `capture(event, properties)` and PostHog-style product
 * analytics in general where every recorded event is one named
 * action plus an optional property bag. Identify/alias/setUser
 * methods are deliberately omitted from this T223 baseline; they
 * land in a future task that wires Clerk's userId into PostHog's
 * `identify()` (currently blocked by T171 Clerk SDK gap).
 */
interface AnalyticsReporter {
    fun track(event: String, properties: Map<String, Any> = emptyMap())
}

/**
 * Canonical PostHog event-name constants for the four T223 events.
 *
 * Defined as constants (not as methods on [AnalyticsReporter]) so
 * the wire-format event names live in one place — server-side
 * dashboards + funnels reference these exact strings. A typo at a
 * call site would create a phantom event in PostHog that doesn't
 * appear in any saved view; constants prevent that class of bug
 * at compile time.
 *
 * The four T223 events:
 *  - [APP_STARTED]: fires once per process from
 *    [com.lap.mobile.LapApplication.onCreate].
 *  - [SESSION_STARTED]: fires when [com.lap.mobile.data.SessionCoordinator.start]
 *    transitions ARMED → RECORDING (after preflight passes).
 *  - [SESSION_FINALIZED]: fires when
 *    [com.lap.mobile.data.SessionCoordinator.stop] completes the
 *    RECORDING → FINALIZING transition + DAO write succeeds.
 *  - [UPLOAD_COMPLETED]: fires from
 *    [com.lap.mobile.upload.UploadBundleWorker] when an upload
 *    reaches `UploadOutcome.Success`.
 *
 * Property keys (e.g., `session_id`, `sample_count`) are not yet
 * part of this constant surface — call sites pass them via the
 * `properties` map of [AnalyticsReporter.track]. Future tasks may
 * promote frequently-used property keys to constants here if the
 * funnel design requires uniform property naming.
 */
object AnalyticsEvent {
    const val APP_STARTED = "app_started"
    const val SESSION_STARTED = "session_started"
    const val SESSION_FINALIZED = "session_finalized"
    const val UPLOAD_COMPLETED = "upload_completed"
}
