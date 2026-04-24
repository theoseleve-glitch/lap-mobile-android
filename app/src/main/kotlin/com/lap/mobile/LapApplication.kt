package com.lap.mobile

import android.app.Application
import com.lap.mobile.core.createLoggingTree
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class LapApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(createLoggingTree(debug = BuildConfig.DEBUG))
    }
}
