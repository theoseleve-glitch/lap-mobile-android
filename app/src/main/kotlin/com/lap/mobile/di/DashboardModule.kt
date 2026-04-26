package com.lap.mobile.di

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
 * Currently only [VoiceAnnouncementPreference] (T207). Future
 * dashboard-related bindings (e.g., LapAnnouncementSpeaker when the
 * actual TextToSpeech wiring lands, possibly a SettingsRepository
 * if T208's high-contrast theme toggle needs persistence) will
 * land in this module — keeping all dashboard-layer wiring in one
 * place keeps the dependency graph obvious to anyone reading
 * `git log -p di/`.
 *
 * Production binds [SharedPreferencesVoiceAnnouncementPreference]
 * which uses regular SharedPreferences; tests bypass Hilt entirely
 * and use an in-memory fake of the [VoiceAnnouncementPreference]
 * interface.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DashboardModule {
    @Binds
    @Singleton
    abstract fun bindVoiceAnnouncementPreference(
        impl: SharedPreferencesVoiceAnnouncementPreference,
    ): VoiceAnnouncementPreference
}
