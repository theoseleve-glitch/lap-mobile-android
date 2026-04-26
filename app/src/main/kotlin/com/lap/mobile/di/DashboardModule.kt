package com.lap.mobile.di

import com.lap.mobile.coaching.CoachingAudioCuePreference
import com.lap.mobile.coaching.SharedPreferencesCoachingAudioCuePreference
import com.lap.mobile.coaching.SharedPreferencesUserSkillLevelPreference
import com.lap.mobile.coaching.UserSkillLevelPreference
import com.lap.mobile.core.SharedPreferencesTelemetryOptOutPreference
import com.lap.mobile.core.TelemetryOptOutPreference
import com.lap.mobile.dashboard.HighContrastThemePreference
import com.lap.mobile.dashboard.SharedPreferencesHighContrastThemePreference
import com.lap.mobile.dashboard.SharedPreferencesVoiceAnnouncementPreference
import com.lap.mobile.dashboard.VoiceAnnouncementPreference
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires the dashboard-layer SingletonComponent bindings.
 *
 * [VoiceAnnouncementPreference] (T207) and
 * [HighContrastThemePreference] (T208). Future dashboard-related
 * bindings (e.g., LapAnnouncementSpeaker when the actual
 * TextToSpeech wiring lands) will land in this module — keeping
 * all dashboard-layer wiring in one place keeps the dependency
 * graph obvious to anyone reading `git log -p di/`.
 *
 * Production binds the SharedPreferences-backed impls (regular,
 * not encrypted — these are UX preferences, not credentials).
 * Tests bypass Hilt entirely and use in-memory fakes of the
 * preference interfaces.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DashboardModule {
    @Binds
    @Singleton
    abstract fun bindVoiceAnnouncementPreference(
        impl: SharedPreferencesVoiceAnnouncementPreference,
    ): VoiceAnnouncementPreference

    @Binds
    @Singleton
    abstract fun bindHighContrastThemePreference(
        impl: SharedPreferencesHighContrastThemePreference,
    ): HighContrastThemePreference

    @Binds
    @Singleton
    abstract fun bindCoachingAudioCuePreference(
        impl: SharedPreferencesCoachingAudioCuePreference,
    ): CoachingAudioCuePreference

    @Binds
    @Singleton
    abstract fun bindUserSkillLevelPreference(
        impl: SharedPreferencesUserSkillLevelPreference,
    ): UserSkillLevelPreference

    @Binds
    @Singleton
    abstract fun bindTelemetryOptOutPreference(
        impl: SharedPreferencesTelemetryOptOutPreference,
    ): TelemetryOptOutPreference
}
