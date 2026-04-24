package com.lap.mobile.di

import com.lap.mobile.core.DefaultDispatcherProvider
import com.lap.mobile.core.DispatcherProvider
import com.lap.mobile.core.lapJson
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

/**
 * SingletonComponent module wiring core-layer dependencies that live for
 * the Application's lifetime.
 *
 * Abstract class form lets us mix `@Binds` (interface-to-impl indirection,
 * cheaper at runtime than @Provides) and `@Provides` via the companion
 * object for things we construct rather than bind.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CoreModule {
    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(impl: DefaultDispatcherProvider): DispatcherProvider

    companion object {
        @Provides
        @Singleton
        fun provideJson(): Json = lapJson
    }
}
