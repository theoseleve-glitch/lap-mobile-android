package com.lap.mobile.coaching

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_FILE_NAME: String = "user_skill_level_prefs"
private const val KEY_LEVEL: String = "level"

/**
 * Android-backed [UserSkillLevelPreference] using regular
 * (NOT encrypted) SharedPreferences.
 *
 * Stored value is the [UserSkillLevel.name] String (e.g.,
 * "INTERMEDIATE"). Unknown stored values (e.g., a future tier
 * a newer client wrote and an older client tries to read after
 * downgrade) fall back to [UserSkillLevel.DEFAULT] rather than
 * throwing — same forward-compat policy as T219
 * [RoomCoachingFeedbackStore]'s unknown-kind fallback.
 *
 * Verification of actual on-disk persistence is [PENDING:
 * device-required] — SharedPreferences is Android-only and JVM
 * tests use the in-memory fake.
 */
@Singleton
class SharedPreferencesUserSkillLevelPreference
@Inject
constructor(
    @ApplicationContext context: Context,
) : UserSkillLevelPreference {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    override fun get(): UserSkillLevel {
        val name = prefs.getString(KEY_LEVEL, null) ?: return UserSkillLevel.DEFAULT
        return runCatching { UserSkillLevel.valueOf(name) }.getOrDefault(UserSkillLevel.DEFAULT)
    }

    override fun set(value: UserSkillLevel) {
        prefs.edit { putString(KEY_LEVEL, value.name) }
    }
}
