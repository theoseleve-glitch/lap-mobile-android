package com.lap.mobile.core

/**
 * No-op [SentryReporter] (T222).
 *
 * Two production roles, plus a test role:
 *  - Default reporter when [isRealSentryDsn] is false (placeholder
 *    DSN in fresh-clone or CI builds) — calls drop on the floor
 *    rather than triggering the Sentry SDK's retry storm against
 *    `o0.ingest.sentry.io`.
 *  - Future T224 telemetry-opt-out target — when the user disables
 *    crash reporting in Settings, [SentryReporter] DI binding swaps
 *    to this object so no events transmit even when init succeeded.
 *  - JVM tests use it as a "reporter is wired but we don't care
 *    what it does" stub.
 *
 * Object (singleton) form mirrors the codebase's other no-op /
 * default-instance convention (see e.g., `Result.Failure(...)` data
 * objects). The interface methods all return Unit so there's no
 * value to compute in the no-op path.
 */
object NoOpSentryReporter : SentryReporter {
    override fun recordBreadcrumb(category: String, message: String, level: SentryReporterLevel) = Unit

    override fun captureMessage(message: String, level: SentryReporterLevel) = Unit

    override fun captureException(throwable: Throwable) = Unit
}
