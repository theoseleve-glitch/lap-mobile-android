package com.lap.mobile.core

/**
 * App-wide Sentry-facing reporter abstraction (T222).
 *
 * Wraps the Sentry SDK's static facade so the rest of the codebase
 * never imports `io.sentry.*` directly — three benefits:
 *  1. Tests inject [NoOpSentryReporter] (or a fake) without touching
 *     the SDK static state.
 *  2. T224 telemetry-opt-out can swap the binding to NoOp without
 *     editing call sites.
 *  3. Future replacement of Sentry with another crash/breadcrumb
 *     backend changes only [SentrySdkReporter] not the dozens of
 *     callers (Logging tree, future upload-error reporting, etc.).
 *
 * Method shape mirrors Sentry's own three primitives — breadcrumbs
 * (sub-event context trail), captureMessage (string-only event), and
 * captureException (throwable-with-stack event) — keeping the
 * abstraction thin enough that callers think in Sentry concepts even
 * when they don't import Sentry types.
 */
interface SentryReporter {
    fun recordBreadcrumb(category: String, message: String, level: SentryReporterLevel = SentryReporterLevel.INFO)

    fun captureMessage(message: String, level: SentryReporterLevel = SentryReporterLevel.ERROR)

    fun captureException(throwable: Throwable)
}

/**
 * Backend-neutral severity levels for [SentryReporter] calls.
 *
 * 1:1 with `io.sentry.SentryLevel` today but keeping a separate
 * enum (translated inside [SentrySdkReporter]) means callers don't
 * leak the Sentry import and a future backend swap costs a single
 * mapping function rather than a global find-and-replace.
 */
enum class SentryReporterLevel { DEBUG, INFO, WARNING, ERROR, FATAL }
