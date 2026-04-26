package com.lap.mobile.core

import io.sentry.SentryOptions

/**
 * Marker substring identifying the placeholder DSN values shipped via
 * BuildConfig in dev/staging/prod flavors. Real Sentry DSNs use a
 * project-specific public key (e.g., `https://abc123def@o12345…`),
 * never the literal `placeholder@` segment. Keeping the marker here
 * (not in the build.gradle.kts placeholders) means a future flavor
 * adding its own placeholder DSN automatically gets the same
 * "skip init" treatment as long as it follows the same naming.
 */
private const val PLACEHOLDER_DSN_MARKER = "placeholder@"

/**
 * Default Sentry sampleRate when [configureSentryOptions] is called
 * without an explicit override. 1.0 = capture every event. Future
 * production tuning (T222 follow-up or telemetry-budget task) may
 * lower this for high-volume builds; right now we need every event
 * during early development to catch the long tail.
 */
private const val DEFAULT_SAMPLE_RATE = 1.0

/**
 * Apply Sentry init configuration to [SentryOptions] (T222 kernel —
 * pure function, no Sentry-init side-effect, no Android dependency).
 *
 * Extracted out of [com.lap.mobile.LapApplication] so the option-
 * setting policy can be JVM-tested without Robolectric or running
 * the actual SDK init handshake. The Android-side caller wraps this
 * in `SentryAndroid.init(context) { configureSentryOptions(it, …) }`.
 *
 * Field choices:
 *  - [dsn] is the project-specific URL from BuildConfig.SENTRY_DSN;
 *    callers should pre-screen via [isRealSentryDsn] and skip init
 *    entirely when the placeholder is in play (avoids retry storms
 *    against `o0.ingest.sentry.io` which doesn't accept the
 *    placeholder project).
 *  - [environment] is one of "dev"/"staging"/"prod" mirroring the
 *    Gradle product flavor name so Sentry's environment filter
 *    splits dev noise from prod incidents.
 *  - [release] is the full `applicationId@versionName+versionCode`
 *    triplet — Sentry's release-tracking joins source-map artifacts
 *    on this exact string, keeping symbolication accurate after
 *    R8 minification ships in T015.
 *  - [debug] mirrors [BuildConfig.DEBUG] so SDK-internal logging
 *    surfaces in dev Logcat but not release.
 *  - [sampleRate] defaults to [DEFAULT_SAMPLE_RATE] = 1.0 (capture
 *    everything during early-stage rollout).
 */
fun configureSentryOptions(
    options: SentryOptions,
    dsn: String,
    environment: String,
    release: String,
    debug: Boolean,
    sampleRate: Double = DEFAULT_SAMPLE_RATE,
) {
    options.dsn = dsn
    options.environment = environment
    options.release = release
    options.isDebug = debug
    options.sampleRate = sampleRate
}

/**
 * True iff [dsn] looks like a real Sentry project DSN rather than
 * one of the BuildConfig placeholders shipped in dev/staging/prod
 * flavors.
 *
 * Why this guard exists: the placeholder DSN
 * `https://placeholder@o0.ingest.sentry.io/<flavor>` is a
 * syntactically-valid DSN that the SDK will accept and start
 * trying to send events to — generating endless 4xx noise in the
 * SDK's internal retry loop and consuming bandwidth + battery.
 * Detecting and skipping init keeps fresh clones (no real DSN
 * provisioned yet) and CI builds (placeholder values committed)
 * silent rather than chatty.
 *
 * Returns false for null, blank, and any DSN containing the
 * literal `placeholder@` host-key segment. Real provisioned DSNs
 * never use `placeholder` as the public key.
 */
fun isRealSentryDsn(dsn: String?): Boolean {
    if (dsn.isNullOrBlank()) return false
    return !dsn.contains(PLACEHOLDER_DSN_MARKER)
}
