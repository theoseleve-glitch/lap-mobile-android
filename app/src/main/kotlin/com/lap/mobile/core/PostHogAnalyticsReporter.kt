package com.lap.mobile.core

import com.posthog.PostHog

/**
 * PostHog SDK-backed [AnalyticsReporter] (T223).
 *
 * Pre-condition: `PostHogAndroid.setup(...)` has already run for
 * the process (typically from
 * [com.lap.mobile.LapApplication.onCreate]). If init was skipped
 * (placeholder key), the SDK's static facade silently drops events
 * — but using [NoOpAnalyticsReporter] in that case is cleaner than
 * relying on the SDK's silence.
 *
 * No Hilt @Inject because there's nothing to inject (PostHog's
 * `capture` is static); a parameterless class is enough for DI to
 * @Provides-instantiate. Stateless so safe to bind as @Singleton.
 *
 * The `if (properties.isNotEmpty())` branch avoids passing an
 * empty map — PostHog's wire format then encodes `properties: {}`
 * which the dashboard renders as a stub property panel; passing
 * `null` (the SDK's default) renders as no-property cleanly.
 */
class PostHogAnalyticsReporter : AnalyticsReporter {
    override fun track(event: String, properties: Map<String, Any>) {
        if (properties.isEmpty()) {
            PostHog.capture(event)
        } else {
            PostHog.capture(event, properties = properties)
        }
    }
}
