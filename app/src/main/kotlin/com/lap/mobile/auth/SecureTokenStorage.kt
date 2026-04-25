package com.lap.mobile.auth

/**
 * Persists short opaque credentials (access token, refresh token) at rest.
 *
 * The interface is intentionally tiny — read / write / clear by key — so
 * the Android-backed [EncryptedPreferencesTokenStorage] can wrap
 * EncryptedSharedPreferences without ceremony, and the JVM
 * `InMemorySecureTokenStorageFake` test double in
 * `SecureTokenStorageContractTest` can implement the same contract for
 * pure-Kotlin unit testing.
 *
 * The "secure" half of the T173 acceptance lives in the Android impl
 * (Keystore-rooted master key + AES256-GCM value encryption); this
 * interface only locks the surface area that callers (login, OkHttp
 * Authenticator T174, logout T175) are allowed to depend on.
 *
 * Reads return `null` when nothing was ever written for that key. That
 * branch is what login screens use to distinguish "first launch / signed
 * out" from "have valid token, restore session".
 *
 * Writes are last-write-wins per key. Token rotation (T174's silent
 * refresh) relies on this — the new access token has to overwrite the
 * stale one, not append.
 */
interface SecureTokenStorage {
    fun read(key: String): String?

    fun write(key: String, value: String)

    fun clear(key: String)

    fun clearAll()
}

/**
 * Well-known keys for [SecureTokenStorage]. Centralized so a typo in one
 * caller (`"refresh.token"` vs `"refresh_token"`) doesn't silently land
 * the value in a slot nobody else reads.
 *
 * Names are dotted (`auth.access_token`) so they namespace cleanly inside
 * the underlying SharedPreferences file — if we ever add unrelated
 * encrypted prefs later (e.g. analytics opt-out), the `auth.*` prefix
 * makes ownership obvious in a hex-dump.
 */
object SecureTokenKeys {
    const val ACCESS_TOKEN: String = "auth.access_token"
    const val REFRESH_TOKEN: String = "auth.refresh_token"
}
