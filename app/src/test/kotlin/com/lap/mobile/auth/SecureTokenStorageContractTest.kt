package com.lap.mobile.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val OPAQUE_ACCESS_TOKEN: String = "eyJhbGciOiJSUzI1NiJ9.payload.signature"
private const val OPAQUE_REFRESH_TOKEN: String = "rt_01JX9Z8M4KTRP5D6Y3V2N7H8KQ"
private const val ROTATED_ACCESS_TOKEN: String = "eyJhbGciOiJSUzI1NiJ9.rotated.signature"
private const val ARBITRARY_KEY: String = "arbitrary.key"

/**
 * Locks the contract every [SecureTokenStorage] impl must honor — the JVM
 * fake here proves the interface is well-defined; the Android-backed
 * [EncryptedPreferencesTokenStorage] obeys the same contract on-device
 * (verification is [PENDING: device-required] because EncryptedSharedPreferences
 * needs Android Keystore hardware).
 *
 * The reason these tests exercise an in-memory fake rather than the real
 * impl is that EncryptedSharedPreferences is a thin façade over Android's
 * Keystore + Tink — at the JVM contract level, what we care about is
 * "given key X with no prior write, read returns null" / "last-write-wins"
 * / "clear(key) only erases that key". Those properties are interface-level,
 * not encryption-level.
 */
class SecureTokenStorageContractTest {
    @Test
    fun read_with_no_prior_write_returns_null_so_caller_can_branch_on_first_launch_vs_returning_user() {
        val storage = InMemorySecureTokenStorageFake()

        assertNull(storage.read(SecureTokenKeys.ACCESS_TOKEN))
    }

    @Test
    fun read_after_write_returns_exact_value_so_authenticator_replays_the_token_byte_for_byte() {
        // Token integrity matters: a JWT or opaque refresh token mangled in
        // storage round-trip would fail signature verification at the server,
        // and the user would see a confusing 401 even though they "just signed in".
        val storage = InMemorySecureTokenStorageFake()

        storage.write(SecureTokenKeys.ACCESS_TOKEN, OPAQUE_ACCESS_TOKEN)

        assertEquals(OPAQUE_ACCESS_TOKEN, storage.read(SecureTokenKeys.ACCESS_TOKEN))
    }

    @Test
    fun second_write_to_same_key_replaces_first_so_token_rotation_doesnt_leave_a_stale_token_behind() {
        // Auth flow: backend rotates access tokens on refresh, OkHttp Authenticator
        // (T174) writes the new one. If write semantics weren't last-write-wins,
        // the next request would replay the stale token and 401-loop.
        val storage = InMemorySecureTokenStorageFake()

        storage.write(SecureTokenKeys.ACCESS_TOKEN, OPAQUE_ACCESS_TOKEN)
        storage.write(SecureTokenKeys.ACCESS_TOKEN, ROTATED_ACCESS_TOKEN)

        assertEquals(ROTATED_ACCESS_TOKEN, storage.read(SecureTokenKeys.ACCESS_TOKEN))
    }

    @Test
    fun clear_erases_only_the_named_key_so_dropping_the_access_token_doesnt_log_user_out_of_refresh_too() {
        // Pattern needed by T174's refresh handler: when access token expires
        // (server returns 401), the refresh token must remain so we can
        // exchange it for a new access token. Conflating clear-one with
        // clear-all would force a full re-login on every access expiry.
        val storage = InMemorySecureTokenStorageFake()
        storage.write(SecureTokenKeys.ACCESS_TOKEN, OPAQUE_ACCESS_TOKEN)
        storage.write(SecureTokenKeys.REFRESH_TOKEN, OPAQUE_REFRESH_TOKEN)

        storage.clear(SecureTokenKeys.ACCESS_TOKEN)

        assertNull(storage.read(SecureTokenKeys.ACCESS_TOKEN))
        assertEquals(OPAQUE_REFRESH_TOKEN, storage.read(SecureTokenKeys.REFRESH_TOKEN))
    }

    @Test
    fun clear_all_erases_every_key_so_logout_T175_truly_drops_every_credential_not_just_the_active_one() {
        val storage = InMemorySecureTokenStorageFake()
        storage.write(SecureTokenKeys.ACCESS_TOKEN, OPAQUE_ACCESS_TOKEN)
        storage.write(SecureTokenKeys.REFRESH_TOKEN, OPAQUE_REFRESH_TOKEN)

        storage.clearAll()

        assertNull(storage.read(SecureTokenKeys.ACCESS_TOKEN))
        assertNull(storage.read(SecureTokenKeys.REFRESH_TOKEN))
    }

    @Test
    fun distinct_keys_hold_distinct_values_so_an_access_token_write_doesnt_silently_clobber_the_refresh_slot() {
        val storage = InMemorySecureTokenStorageFake()

        storage.write(SecureTokenKeys.ACCESS_TOKEN, OPAQUE_ACCESS_TOKEN)
        storage.write(SecureTokenKeys.REFRESH_TOKEN, OPAQUE_REFRESH_TOKEN)

        assertEquals(OPAQUE_ACCESS_TOKEN, storage.read(SecureTokenKeys.ACCESS_TOKEN))
        assertEquals(OPAQUE_REFRESH_TOKEN, storage.read(SecureTokenKeys.REFRESH_TOKEN))
    }

    @Test
    fun clear_on_a_key_that_was_never_written_is_a_noop_so_idempotent_logout_doesnt_throw() {
        // Logout (T175) calls clear() on every well-known key. If the user
        // had logged out previously and then re-launched without signing in,
        // those keys would already be empty. clear() must tolerate that
        // instead of throwing — otherwise idempotent cleanup becomes fragile.
        val storage = InMemorySecureTokenStorageFake()

        storage.clear(ARBITRARY_KEY)

        assertNull(storage.read(ARBITRARY_KEY))
    }
}

/**
 * Test-only in-memory [SecureTokenStorage]. Lives in the same file as the
 * tests so the contract being asserted and the fake under test are visible
 * together; production code never touches it.
 *
 * Suffixed `Fake` (rather than the bare `InMemorySecureTokenStorage` name)
 * so future test files in this package don't collide via Kotlin's
 * file-private-is-package-private-at-JVM behavior — same trick used in
 * the upload tests (RetryFakeQueueDao, KillReplayFakeQueueDao, …).
 */
private class InMemorySecureTokenStorageFake : SecureTokenStorage {
    private val store = mutableMapOf<String, String>()

    override fun read(key: String): String? = store[key]

    override fun write(key: String, value: String) {
        store[key] = value
    }

    override fun clear(key: String) {
        store.remove(key)
    }

    override fun clearAll() {
        store.clear()
    }
}
