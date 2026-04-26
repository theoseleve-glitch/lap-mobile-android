package com.lap.mobile

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.lap.mobile.core.AnalyticsEvent
import com.lap.mobile.core.AnalyticsReporter
import com.lap.mobile.core.NoOpSentryReporter
import com.lap.mobile.core.OptOutAwareSentryReporter
import com.lap.mobile.core.SentryReporter
import com.lap.mobile.core.SentrySdkReporter
import com.lap.mobile.core.TelemetryOptOutPreference
import com.lap.mobile.core.configureSentryOptions
import com.lap.mobile.core.createLoggingTree
import com.lap.mobile.core.isRealPostHogKey
import com.lap.mobile.core.isRealSentryDsn
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid
import timber.log.Timber
import javax.inject.Inject

/**
 * Hosts WorkManager's [Configuration] so that the runtime resolves
 * `@HiltWorker` constructors via [HiltWorkerFactory] (T146). Without
 * the [Configuration.Provider] hook, WorkManager auto-initialises
 * with a default `WorkerFactory` and HiltWorkers fail at construction
 * with `IllegalStateException: Could not instantiate ...` — at which
 * point the upload queue (T147+) is silently dead in the water.
 *
 * The provider hook is the only WorkManager bootstrapping the app
 * needs: AndroidManifest still uses the default `WorkManagerInitializer`
 * (auto-init), but it pulls our [Configuration] via
 * [Configuration.Provider] when the first request lands.
 *
 * T222 also wires Sentry SDK init here. Two-step bootstrap — the
 * Sentry init handshake runs before [Timber.plant] so the logging
 * tree (which forwards warn/error to Sentry) is ready the moment
 * the first downstream Timber call lands. The init is gated by
 * [isRealSentryDsn]: placeholder DSNs from dev/staging/prod build
 * flavors skip init entirely, returning [NoOpSentryReporter] so the
 * tree's forwarding-side is silent rather than wedging the SDK
 * against an invalid DSN.
 */
@HiltAndroidApp
class LapApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var analyticsReporter: AnalyticsReporter

    @Inject
    lateinit var telemetryOptOutPreference: TelemetryOptOutPreference

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        val sentryReporter = initSentryIfConfigured()
        Timber.plant(createLoggingTree(debug = BuildConfig.DEBUG, sentryReporter = sentryReporter))
        initPostHogIfConfigured()
        analyticsReporter.track(AnalyticsEvent.APP_STARTED)
    }

    private fun initSentryIfConfigured(): SentryReporter {
        val base = baseSentryReporter()
        // T224: wrap in OptOutAware so the Settings toggle suppresses
        // every subsequent forward (breadcrumb / message / exception)
        // without re-initializing the SDK underneath. Wrapping the
        // NoOp branch is harmless + keeps the LapApplication wiring
        // uniform across real-DSN and placeholder-DSN paths.
        return OptOutAwareSentryReporter(base, telemetryOptOutPreference)
    }

    private fun baseSentryReporter(): SentryReporter {
        val dsn = BuildConfig.SENTRY_DSN
        if (!isRealSentryDsn(dsn)) {
            return NoOpSentryReporter
        }
        SentryAndroid.init(this) { options ->
            configureSentryOptions(
                options = options,
                dsn = dsn,
                environment = BuildConfig.FLAVOR,
                release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}",
                debug = BuildConfig.DEBUG,
            )
        }
        return SentrySdkReporter()
    }

    /**
     * T223: PostHog SDK init guarded the same way Sentry is — placeholder
     * keys (`phc_placeholder_<flavor>`) skip init entirely so the SDK's
     * batching loop doesn't run against a dead key. The Hilt-bound
     * [AnalyticsReporter] (see [com.lap.mobile.di.AnalyticsModule])
     * is statically resolved to [com.lap.mobile.core.NoOpAnalyticsReporter]
     * in that case so subsequent `track()` calls are silent.
     *
     * Why init in onCreate (not from the @Provides method): same
     * reason as Sentry — keeps the Application-scoped lifecycle
     * bootstrap in one place, makes the init order obvious in
     * `git log -p LapApplication.kt` and lets a future task that
     * needs to re-init (e.g., toggling captureScreenViews from
     * Settings) hold the handle on this class rather than fighting
     * Hilt's component lifecycle.
     */
    private fun initPostHogIfConfigured() {
        val apiKey = BuildConfig.POSTHOG_KEY
        if (!isRealPostHogKey(apiKey)) {
            return
        }
        val config = PostHogAndroidConfig(apiKey = apiKey).apply {
            debug = BuildConfig.DEBUG
        }
        PostHogAndroid.setup(this, config)
    }
}
