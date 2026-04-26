package com.lap.mobile.di

import com.lap.mobile.BuildConfig
import com.lap.mobile.core.AnalyticsReporter
import com.lap.mobile.core.NoOpAnalyticsReporter
import com.lap.mobile.core.OptOutAwareAnalyticsReporter
import com.lap.mobile.core.PostHogAnalyticsReporter
import com.lap.mobile.core.TelemetryOptOutPreference
import com.lap.mobile.core.isRealPostHogKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires the [AnalyticsReporter] binding (T223).
 *
 * Selects between [PostHogAnalyticsReporter] (real SDK calls) and
 * [NoOpAnalyticsReporter] (placeholder-key path) based on
 * [BuildConfig.POSTHOG_KEY] at app-component-creation time —
 * statically-decided per build flavor so a fresh clone with the
 * placeholder key automatically gets the no-op binding.
 *
 * Pre-condition for the PostHog branch: `PostHogAndroid.setup(...)`
 * has run from [com.lap.mobile.LapApplication.onCreate] before any
 * `track()` call. The provider itself doesn't init the SDK — it
 * just constructs the thin reporter wrapper. Init lives in
 * LapApplication so the same flow can pre-init Sentry alongside
 * PostHog (T222) before the first downstream Hilt-injected
 * consumer fires.
 *
 * T224 (telemetry opt-out) will replace this binding with a
 * runtime-toggleable wrapper that swaps to NoOp when the user
 * disables analytics in Settings; today's binding is fixed at
 * Hilt-component-creation time.
 */
@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {
    @Provides
    @Singleton
    fun provideAnalyticsReporter(optOutPreference: TelemetryOptOutPreference): AnalyticsReporter {
        val base = if (isRealPostHogKey(BuildConfig.POSTHOG_KEY)) {
            PostHogAnalyticsReporter()
        } else {
            NoOpAnalyticsReporter
        }
        // T224: wrap in OptOutAware so a Settings toggle flip
        // suppresses every subsequent track() call without re-
        // wiring the SDK underneath. Wrapping the NoOp branch is
        // harmless + keeps the wiring uniform.
        return OptOutAwareAnalyticsReporter(base, optOutPreference)
    }
}
