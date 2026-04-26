package com.lap.mobile.core

/**
 * User-controlled opt-out flag for Sentry crash reporting + PostHog
 * product analytics (T224).
 *
 * `isOptedOut() == true` means the user has explicitly disabled
 * telemetry from Settings; both [OptOutAwareSentryReporter] and
 * [OptOutAwareAnalyticsReporter] gate every call on this check
 * before forwarding to the underlying SDK-backed reporters.
 *
 * Default policy is opt-IN ([DEFAULT_OPTED_OUT] = false) — telemetry
 * runs unless the user explicitly opts out. This matches the
 * existing T207/T208/T218 settings-pref pattern (boolean toggle
 * defaulting OFF, where OFF means "feature inactive") interpreted
 * inversely: the "feature" here is the opt-OUT, default OFF =
 * not opted-out = telemetry on. The product team can flip the
 * default to opt-OUT later if a privacy-consent screen ships
 * (out of scope for T224).
 */
interface TelemetryOptOutPreference {
    fun isOptedOut(): Boolean

    fun setOptedOut(optedOut: Boolean)

    companion object {
        const val DEFAULT_OPTED_OUT: Boolean = false
    }
}
