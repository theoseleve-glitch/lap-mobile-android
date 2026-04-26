package com.lap.mobile.core

import android.util.Log
import timber.log.Timber

/**
 * Default breadcrumb category used when [Timber.Tree.log] passes a
 * null tag (typically when callers use `Timber.tag(null)` or call
 * the static facade without a tag — Timber falls back to the calling
 * class's simple name except in proguarded release builds where the
 * stack-trace lookup fails).
 */
private const val DEFAULT_BREADCRUMB_CATEGORY = "log"

/**
 * Picks the Timber tree for the current build. Extracted out of
 * LapApplication so it can be unit-tested in pure JVM without Robolectric.
 *
 * - debug=true  → Timber.DebugTree (everything to Logcat, tag = class name)
 * - debug=false → ReleaseLoggingTree (warn+ only; T222 forwards to Sentry
 *   via [sentryReporter] when a real DSN is configured)
 */
internal fun createLoggingTree(debug: Boolean, sentryReporter: SentryReporter = NoOpSentryReporter): Timber.Tree {
    return if (debug) Timber.DebugTree() else ReleaseLoggingTree(sentryReporter)
}

internal class ReleaseLoggingTree(
    private val sentryReporter: SentryReporter = NoOpSentryReporter,
) : Timber.Tree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.WARN

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val suffix = if (t != null) "\n${Log.getStackTraceString(t)}" else ""
        Log.println(priority, tag, message + suffix)
        forwardToSentry(
            reporter = sentryReporter,
            priority = priority,
            category = tag ?: DEFAULT_BREADCRUMB_CATEGORY,
            message = message,
            throwable = t,
        )
    }
}

/**
 * Pure routing policy mapping a Timber log call to a [SentryReporter]
 * call (T222 kernel — JVM-testable without Robolectric).
 *
 * Mapping:
 *  - WARN          → recordBreadcrumb at WARNING level
 *  - ERROR/ASSERT with throwable    → captureException
 *  - ERROR/ASSERT without throwable → captureMessage at ERROR level
 *  - INFO/DEBUG/VERBOSE → no-op (the tree's [Timber.Tree.isLoggable]
 *    filter already blocks these from reaching [forwardToSentry] in
 *    production, but the explicit no-op here keeps the function safe
 *    if a future caller bypasses the filter)
 *
 * The category defaults to [DEFAULT_BREADCRUMB_CATEGORY] when the
 * caller passes a missing/null tag — Sentry breadcrumbs require a
 * non-null category for grouping in the issue UI.
 */
internal fun forwardToSentry(
    reporter: SentryReporter,
    priority: Int,
    category: String,
    message: String,
    throwable: Throwable?,
) {
    when (priority) {
        Log.WARN -> reporter.recordBreadcrumb(
            category = category,
            message = message,
            level = SentryReporterLevel.WARNING,
        )
        Log.ERROR, Log.ASSERT -> {
            if (throwable != null) {
                reporter.captureException(throwable)
            } else {
                reporter.captureMessage(message, SentryReporterLevel.ERROR)
            }
        }
    }
}
