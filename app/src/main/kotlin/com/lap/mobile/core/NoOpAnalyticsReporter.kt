package com.lap.mobile.core

/**
 * No-op [AnalyticsReporter] (T223).
 *
 * Three roles mirroring [NoOpSentryReporter]:
 *  - Default reporter when [isRealPostHogKey] is false (placeholder
 *    key in fresh-clone / CI builds) — calls drop on the floor
 *    rather than triggering the PostHog SDK's batching loop against
 *    a dead key + draining battery.
 *  - T224 telemetry-opt-out target — when the user disables product
 *    analytics in Settings, the [AnalyticsReporter] DI binding
 *    swaps to this object so no events transmit even when init
 *    succeeded.
 *  - JVM tests use it as the "reporter is wired but we don't care
 *    what it does" stub.
 *
 * Object form mirrors the codebase's other no-op default-instance
 * convention (see [NoOpSentryReporter]).
 */
object NoOpAnalyticsReporter : AnalyticsReporter {
    override fun track(event: String, properties: Map<String, Any>) = Unit
}
