package com.lap.mobile.core

/**
 * [SentryReporter] decorator that gates every call on a
 * [TelemetryOptOutPreference] check (T224).
 *
 * When the user has opted out from Settings, every method
 * short-circuits — no breadcrumb is recorded, no event captured.
 * The check happens per-call (not once at construction) so a
 * runtime preference flip from the Settings toggle takes effect
 * immediately for subsequent log lines.
 *
 * Wraps EITHER [SentrySdkReporter] (real DSN configured) or
 * [NoOpSentryReporter] (placeholder DSN). Wrapping NoOp is
 * harmless and keeps the LapApplication wiring uniform — both
 * branches return the same OptOutAware-wrapped type.
 *
 * Why a per-call check rather than caching the boolean: the
 * preference is read from SharedPreferences which is in-memory
 * cached after the first read, so per-call cost is a single map
 * lookup. This avoids the pitfall of a stale-cache value
 * persisting across a Settings change while the user expects
 * "I just toggled this off — why is the next crash still
 * being reported?".
 */
class OptOutAwareSentryReporter(
    private val delegate: SentryReporter,
    private val optOutPreference: TelemetryOptOutPreference,
) : SentryReporter {
    override fun recordBreadcrumb(category: String, message: String, level: SentryReporterLevel) {
        if (optOutPreference.isOptedOut()) return
        delegate.recordBreadcrumb(category, message, level)
    }

    override fun captureMessage(message: String, level: SentryReporterLevel) {
        if (optOutPreference.isOptedOut()) return
        delegate.captureMessage(message, level)
    }

    override fun captureException(throwable: Throwable) {
        if (optOutPreference.isOptedOut()) return
        delegate.captureException(throwable)
    }
}
