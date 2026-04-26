package com.lap.mobile.core

import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

/**
 * Sentry SDK-backed [SentryReporter] (T222).
 *
 * Pre-condition: `SentryAndroid.init(...)` has already run for the
 * process (typically from [com.lap.mobile.LapApplication.onCreate]).
 * If init was skipped (placeholder DSN), the SDK's static facade
 * silently drops events — but using [NoOpSentryReporter] in that
 * case is cleaner than relying on the SDK's silence.
 *
 * No Hilt @Inject because there's nothing to inject (Sentry's API
 * is fully static); a parameterless class is enough for DI to
 * @Provides-instantiate. Stateless so safe to bind as @Singleton.
 */
class SentrySdkReporter : SentryReporter {
    override fun recordBreadcrumb(category: String, message: String, level: SentryReporterLevel) {
        val breadcrumb = Breadcrumb().apply {
            this.category = category
            this.message = message
            this.level = level.toSentryLevel()
        }
        Sentry.addBreadcrumb(breadcrumb)
    }

    override fun captureMessage(message: String, level: SentryReporterLevel) {
        Sentry.captureMessage(message, level.toSentryLevel())
    }

    override fun captureException(throwable: Throwable) {
        Sentry.captureException(throwable)
    }
}

internal fun SentryReporterLevel.toSentryLevel(): SentryLevel = when (this) {
    SentryReporterLevel.DEBUG -> SentryLevel.DEBUG
    SentryReporterLevel.INFO -> SentryLevel.INFO
    SentryReporterLevel.WARNING -> SentryLevel.WARNING
    SentryReporterLevel.ERROR -> SentryLevel.ERROR
    SentryReporterLevel.FATAL -> SentryLevel.FATAL
}
