package com.lap.mobile

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.lap.mobile.core.createLoggingTree
import dagger.hilt.android.HiltAndroidApp
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
        Timber.plant(createLoggingTree(debug = BuildConfig.DEBUG))
    }
}
