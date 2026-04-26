package com.lap.mobile.coaching

/**
 * User's self-reported skill-level setting (T221).
 *
 * Two-method contract — read the current state, write the user's
 * choice. Storage-agnostic so [parseInsightPromptPayload] kernel
 * stays Android-free and tests can swap an in-memory fake.
 *
 * Production binds [SharedPreferencesUserSkillLevelPreference]
 * via [com.lap.mobile.di.DashboardModule]. Regular SharedPreferences
 * (NOT EncryptedSharedPreferences) — this is a UX preference, not
 * a credential. Same security analysis as T207 voice toggle.
 *
 * Default is [UserSkillLevel.DEFAULT] (INTERMEDIATE) for two
 * reasons documented in [UserSkillLevel]: most users self-categorize
 * as intermediate when first asked, and intermediate is the safest
 * fallback when a rule's body variants are missing the user's
 * actual tier.
 */
interface UserSkillLevelPreference {
    fun get(): UserSkillLevel

    fun set(value: UserSkillLevel)
}
