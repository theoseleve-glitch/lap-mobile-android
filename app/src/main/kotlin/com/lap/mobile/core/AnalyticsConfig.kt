package com.lap.mobile.core

/**
 * Real PostHog API keys are issued in the format `phc_<random>`. The
 * prefix gate filters out non-PostHog values that might appear in
 * BuildConfig (e.g., a paste-error from another vendor's key) before
 * they hit the SDK init handshake.
 */
private const val POSTHOG_KEY_PREFIX = "phc_"

/**
 * BuildConfig ships `phc_placeholder_<flavor>` for dev/staging/prod
 * until a real PostHog project is provisioned (T015). The literal
 * `placeholder` segment in the issued key disqualifies it as a
 * real key — same defensive shape as [isRealSentryDsn]'s placeholder
 * detection (T222).
 */
private const val POSTHOG_PLACEHOLDER_INFIX = "placeholder"

/**
 * True iff [apiKey] looks like a real PostHog project key rather
 * than one of the BuildConfig placeholders (T223).
 *
 * Why this guard exists: PostHog's `setup` accepts any string
 * starting with `phc_` as a valid-looking key and starts batching
 * events against the configured host. The placeholder values
 * `phc_placeholder_<flavor>` would surface as failed POSTs with
 * `401 Unauthorized` from the ingest endpoint, draining battery
 * and bandwidth on fresh-clone or CI builds. Detecting + skipping
 * init keeps those builds silent.
 *
 * Returns false for null, blank, anything not prefixed with
 * `phc_`, and any key containing the literal `placeholder`
 * substring. Real provisioned keys never include `placeholder`.
 */
fun isRealPostHogKey(apiKey: String?): Boolean {
    if (apiKey.isNullOrBlank()) return false
    if (!apiKey.startsWith(POSTHOG_KEY_PREFIX)) return false
    return !apiKey.contains(POSTHOG_PLACEHOLDER_INFIX)
}
