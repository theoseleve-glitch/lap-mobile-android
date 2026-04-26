package com.lap.mobile.di

import com.lap.mobile.coaching.CoachingFeedbackStore
import com.lap.mobile.coaching.RoomCoachingFeedbackStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires the coaching-layer SingletonComponent bindings (T219+).
 *
 * Today: just the [CoachingFeedbackStore] → [RoomCoachingFeedbackStore]
 * binding. As Block K progresses, additional coaching-layer wiring
 * (BundleResolution orchestrator, SessionCoachingHook factory, etc.)
 * will land here so the dependency graph stays obvious to anyone
 * reading `git log -p di/`.
 *
 * Why a separate module rather than adding to [DashboardModule]:
 * DashboardModule houses preference interfaces backed by
 * SharedPreferences (UX toggles for voice / theme / coaching audio
 * cue). The coaching feedback store is backed by Room with a
 * different lifecycle and dependency shape (DAO + Clock vs Context).
 * Splitting keeps each module's bindings cohesive.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CoachingModule {
    @Binds
    @Singleton
    abstract fun bindCoachingFeedbackStore(impl: RoomCoachingFeedbackStore): CoachingFeedbackStore
}
