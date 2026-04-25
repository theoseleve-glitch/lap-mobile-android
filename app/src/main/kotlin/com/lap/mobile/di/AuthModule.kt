package com.lap.mobile.di

import com.lap.mobile.auth.BiometricLockPreference
import com.lap.mobile.auth.EncryptedPreferencesTokenStorage
import com.lap.mobile.auth.SecureTokenStorage
import com.lap.mobile.auth.SharedPreferencesBiometricLockPreference
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires the auth-layer SingletonComponent bindings.
 *
 * Currently only [SecureTokenStorage] (T173). T174's OkHttp Authenticator
 * and T175's logout dropper will land in this module as they're built —
 * keeping all auth-layer wiring in one place keeps the dependency graph
 * obvious to anyone reading `git log -p di/`.
 *
 * Production binds [EncryptedSharedPreferences] via
 * [EncryptedPreferencesTokenStorage]; tests bypass Hilt entirely and use
 * the in-memory fake declared inside `SecureTokenStorageContractTest`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    @Singleton
    abstract fun bindSecureTokenStorage(impl: EncryptedPreferencesTokenStorage): SecureTokenStorage

    @Binds
    @Singleton
    abstract fun bindBiometricLockPreference(impl: SharedPreferencesBiometricLockPreference): BiometricLockPreference
}
