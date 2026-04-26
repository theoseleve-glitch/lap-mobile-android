package com.lap.mobile.core

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_FILE_NAME: String = "telemetry_opt_out_prefs"
private const val KEY_OPTED_OUT: String = "opted_out"

/**
 * Android-backed [TelemetryOptOutPreference] using regular
 * (NOT encrypted) SharedPreferences (T224).
 *
 * Stored value is a Boolean (`true` = opted-out, `false` =
 * opted-in). Missing key (fresh install) returns
 * [TelemetryOptOutPreference.DEFAULT_OPTED_OUT] = false so the
 * default behavior is "telemetry on" matching the standard
 * mobile-app UX.
 *
 * Same separate-prefs-file pattern as T207/T208/T218/T221 —
 * keeps each preference in its own file so toggle storage stays
 * cleanly partitioned. Uses `apply()` (asynchronous write) not
 * `commit()` because a write lost to process death is annoying
 * (the toggle reverts) but not safety-critical, and `apply()` is
 * non-blocking on the main thread.
 *
 * Verification of actual on-disk persistence is [PENDING:
 * device-required] — SharedPreferences is Android-only and JVM
 * tests use the in-memory fake.
 */
@Singleton
class SharedPreferencesTelemetryOptOutPreference
@Inject
constructor(
    @ApplicationContext context: Context,
) : TelemetryOptOutPreference {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    override fun isOptedOut(): Boolean = prefs.getBoolean(KEY_OPTED_OUT, TelemetryOptOutPreference.DEFAULT_OPTED_OUT)

    override fun setOptedOut(optedOut: Boolean) {
        prefs.edit { putBoolean(KEY_OPTED_OUT, optedOut) }
    }
}
