package com.lap.mobile.auth

import org.junit.Assert.assertEquals
import org.junit.Test

private const val ACCESS: String = "access-v1"
private const val REFRESH: String = "refresh-v1"

/**
 * Kernel for the Settings paired-device indicator (T181). Maps the
 * current [SecureTokenStorage] state to a [PairedDeviceState] that the
 * Compose indicator renders.
 *
 * Why derive from storage rather than store an explicit "isPaired"
 * boolean: storage is already the canonical source of truth. T175's
 * logout wipes both tokens, T179's redeem writes both, T172's login
 * (when wired) writes both. Any code path that wants to flip the
 * pairing state would have to update both — and we'd have to keep
 * both in sync. Derivation collapses two-things-must-agree into one.
 *
 * Why REFRESH_TOKEN, not ACCESS_TOKEN: access tokens live ~minutes
 * (T174 silent-refresh rotates them). If the indicator gated on
 * access presence, it would flicker every time silent-refresh ran
 * mid-rotation. Refresh tokens span the device's pairing lifetime —
 * present iff the device can talk to the server long-term, which is
 * exactly the user's mental model of "is this device paired".
 */
class PairedDeviceStateResolverTest {
    @Test
    fun no_tokens_in_storage_resolves_to_not_paired_so_first_launch_shows_pair_this_device() {
        val storage = ResolverTokenStorageFake()

        val state = PairedDeviceStateResolver.resolve(storage)

        assertEquals(PairedDeviceState.NotPaired, state)
    }

    @Test
    fun only_access_token_present_resolves_to_not_paired_because_access_alone_expires_in_minutes() {
        // Edge case that shouldn't happen via normal flows (T179
        // writes both halves, T175 wipes both), but if it ever does
        // — partial write race, manual debugger poke — calling that
        // "Paired" would mislead the user. Without refresh, the next
        // 401 forces re-pair anyway.
        val storage = ResolverTokenStorageFake()
        storage.write(SecureTokenKeys.ACCESS_TOKEN, ACCESS)

        val state = PairedDeviceStateResolver.resolve(storage)

        assertEquals(PairedDeviceState.NotPaired, state)
    }

    @Test
    fun refresh_token_present_resolves_to_paired_because_refresh_is_the_durable_pairing_marker() {
        val storage = ResolverTokenStorageFake()
        storage.write(SecureTokenKeys.REFRESH_TOKEN, REFRESH)

        val state = PairedDeviceStateResolver.resolve(storage)

        assertEquals(PairedDeviceState.Paired(identifier = null), state)
    }

    @Test
    fun both_tokens_present_resolves_to_paired_normal_post_pair_steady_state() {
        val storage = ResolverTokenStorageFake()
        storage.write(SecureTokenKeys.ACCESS_TOKEN, ACCESS)
        storage.write(SecureTokenKeys.REFRESH_TOKEN, REFRESH)

        val state = PairedDeviceStateResolver.resolve(storage)

        assertEquals(PairedDeviceState.Paired(identifier = null), state)
    }

    @Test
    fun blank_refresh_token_resolves_to_not_paired_so_a_corrupted_empty_value_doesnt_lie_to_the_user() {
        // Defensive: a partial write or restored backup landing an
        // empty string in the refresh slot must not surface as
        // "Paired" — the next /auth/refresh would fail with that
        // empty token (T174 already filters blank, but the indicator
        // must not lie about state regardless).
        val storage = ResolverTokenStorageFake()
        storage.write(SecureTokenKeys.REFRESH_TOKEN, "   ")

        val state = PairedDeviceStateResolver.resolve(storage)

        assertEquals(PairedDeviceState.NotPaired, state)
    }
}

private class ResolverTokenStorageFake : SecureTokenStorage {
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
