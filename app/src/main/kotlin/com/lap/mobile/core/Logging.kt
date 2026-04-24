package com.lap.mobile.core

import android.util.Log
import timber.log.Timber

/**
 * Picks the Timber tree for the current build. Extracted out of
 * LapApplication so it can be unit-tested in pure JVM without Robolectric.
 *
 * - debug=true  → Timber.DebugTree (everything to Logcat, tag = class name)
 * - debug=false → ReleaseLoggingTree (warn+ only; T222 layers Sentry on top)
 */
internal fun createLoggingTree(debug: Boolean): Timber.Tree {
    return if (debug) Timber.DebugTree() else ReleaseLoggingTree()
}

internal class ReleaseLoggingTree : Timber.Tree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.WARN

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val suffix = if (t != null) "\n${Log.getStackTraceString(t)}" else ""
        Log.println(priority, tag, message + suffix)
    }
}
