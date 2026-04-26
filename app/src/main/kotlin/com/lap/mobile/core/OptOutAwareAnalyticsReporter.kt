package com.lap.mobile.core

/**
 * [AnalyticsReporter] decorator that gates every event on a
 * [TelemetryOptOutPreference] check (T224).
 *
 * When the user has opted out from Settings, [track] short-
 * circuits without forwarding to the underlying reporter. The
 * check happens per-call (not once at construction) so a runtime
 * preference flip from the Settings toggle takes effect
 * immediately for subsequent events.
 *
 * Wraps EITHER [PostHogAnalyticsReporter] (real key configured)
 * or [NoOpAnalyticsReporter] (placeholder key). Wrapping NoOp is
 * harmless and keeps the AnalyticsModule wiring uniform — both
 * branches return the same OptOutAware-wrapped type.
 */
class OptOutAwareAnalyticsReporter(
    private val delegate: AnalyticsReporter,
    private val optOutPreference: TelemetryOptOutPreference,
) : AnalyticsReporter {
    override fun track(event: String, properties: Map<String, Any>) {
        if (optOutPreference.isOptedOut()) return
        delegate.track(event, properties)
    }
}
