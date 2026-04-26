package com.lap.mobile

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.lap.mobile.core.NoOpSentryReporter
import com.lap.mobile.core.SentryReporter
import com.lap.mobile.core.SentrySdkReporter
import com.lap.mobile.core.configureSentryOptions
import com.lap.mobile.core.createLoggingTree
import com.lap.mobile.core.isRealSentryDsn
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

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        val sentryReporter = initSentryIfConfigured()
        Timber.plant(createLoggingTree(debug = BuildConfig.DEBUG, sentryReporter = sentryReporter))
    }

    private fun initSentryIfConfigured(): SentryReporter {
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
}
