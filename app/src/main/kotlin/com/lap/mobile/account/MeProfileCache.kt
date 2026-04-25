package com.lap.mobile.account

/**
 * Single-slot cache for the active user's [MeProfile] (T189).
 *
 * Three-method contract — read the current entry, write a fresh
 * one, drop it on logout / session expiry. Storage-agnostic so
 * future persistent impls (SharedPreferences-backed,
 * EncryptedSharedPreferences-backed) can swap in without changing
 * call sites.
 *
 * v1 production binding is [InMemoryMeProfileCache] — process-local.
 * Cold launch with cached refresh token re-fires
 * [SyncMeProfileUseCase] via T166's auth-restore kick path, so the
 * cache always re-populates within ~one network round-trip of the
 * app reaching foreground. Persistent storage is a speculative-PII
 * concern (email, displayName on disk) — not landing it in v1 keeps
 * the surface area minimal until there's a clear UX case for
 * "show last user's email pre-login".
 *
 * Single-slot semantics: the cache holds at most one profile (the
 * currently-active user's). Multi-account installs (T187 forward-
 * compat) would split this into a map keyed by AccountId; the
 * single-slot shape is the v1 simplification matching v1's
 * single-account semantics.
 */
interface MeProfileCache {
    fun read(): MeProfile?

    fun write(profile: MeProfile)

    fun clear()
}
