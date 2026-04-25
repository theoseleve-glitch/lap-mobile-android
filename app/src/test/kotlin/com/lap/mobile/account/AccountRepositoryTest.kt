package com.lap.mobile.account

import com.lap.mobile.auth.SecureTokenKeys
import com.lap.mobile.auth.SecureTokenStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val REFRESH_PRESENT: String = "refresh-token-present"

/**
 * Multi-account scaffold (T187) — locks the contract for an
 * [AccountRepository] that exposes a list-shaped account API
 * (knownAccounts) but is backed by single-account semantics in v1.
 *
 * The "not user-facing" acceptance means no UI surfaces this yet —
 * the value is forward-compatibility. When future versions add
 * "switch active account" / "add another account", the repo's API
 * shape doesn't change; only the impl grows.
 *
 * Derivation source: [SecureTokenStorage] (T173). Presence of
 * `REFRESH_TOKEN` ⇒ "we're paired to one account". Same predicate
 * T181's [com.lap.mobile.auth.PairedDeviceStateResolver] uses, but
 * exposing an [Account] rather than a UI-pill state. Single source
 * of truth means logout (T175) and redeem (T179) automatically flip
 * this without explicit "set active account" calls.
 *
 * Account ID is a placeholder (`"active"`) until T189's /me sync
 * fetches the real server-side account identifier. A real account
 * ID lets multi-account installs disambiguate; the placeholder is
 * fine for v1 since there's only ever one.
 */
class AccountRepositoryTest {
    @Test
    fun no_refresh_token_means_no_current_account_so_anonymous_first_launch_returns_null() {
        val storage = AccountTokenStorageFake()
        val repo = AccountRepository(storage)

        val current = repo.currentAccount()

        assertNull(current)
    }

    @Test
    fun no_refresh_token_means_known_accounts_is_empty_so_future_account_chooser_shows_pair_this_device_cta() {
        val storage = AccountTokenStorageFake()
        val repo = AccountRepository(storage)

        val known = repo.knownAccounts()

        assertTrue("expected empty knownAccounts when not paired, got: $known", known.isEmpty())
    }

    @Test
    fun refresh_token_present_returns_a_current_account_with_synthesized_placeholder_id() {
        val storage = AccountTokenStorageFake()
        storage.write(SecureTokenKeys.REFRESH_TOKEN, REFRESH_PRESENT)
        val repo = AccountRepository(storage)

        val current = repo.currentAccount()

        assertEquals(AccountId(value = "active"), current?.id)
    }

    @Test
    fun current_account_display_label_is_null_until_T189_me_sync_lands() {
        // We don't know who the user is server-side until T189
        // fetches /me. The label slot is reserved so future task
        // can populate without changing the Account shape.
        val storage = AccountTokenStorageFake()
        storage.write(SecureTokenKeys.REFRESH_TOKEN, REFRESH_PRESENT)
        val repo = AccountRepository(storage)

        val current = repo.currentAccount()

        assertNull(current?.displayLabel)
    }

    @Test
    fun known_accounts_returns_single_element_list_when_paired_so_v1_keeps_list_shape() {
        // Forward-compat: v1 has at most one account but the API is
        // already list-shaped. Future "add another account" doesn't
        // change the call site — just changes the list size.
        val storage = AccountTokenStorageFake()
        storage.write(SecureTokenKeys.REFRESH_TOKEN, REFRESH_PRESENT)
        val repo = AccountRepository(storage)

        val known = repo.knownAccounts()

        assertEquals(1, known.size)
    }

    @Test
    fun blank_refresh_token_resolves_to_no_current_account_defensive_against_corrupted_partial_write() {
        // Same defensive case as T181's PairedDeviceStateResolver —
        // a partial write or restored backup landing an empty string
        // in the refresh slot must not surface a fake "Paired"
        // account.
        val storage = AccountTokenStorageFake()
        storage.write(SecureTokenKeys.REFRESH_TOKEN, "   ")
        val repo = AccountRepository(storage)

        val current = repo.currentAccount()

        assertNull(current)
    }

    @Test
    fun account_id_constructor_rejects_blank_value_so_a_future_caller_cant_silently_smuggle_empty_id() {
        // Type-safe wrapper — the invariant catches partial-state
        // bugs at construction rather than 200 lines downstream.
        assertThrows(IllegalArgumentException::class.java) {
            AccountId(value = "")
        }
    }

    @Test
    fun account_id_constructor_rejects_whitespace_only_value() {
        assertThrows(IllegalArgumentException::class.java) {
            AccountId(value = "   ")
        }
    }
}

private class AccountTokenStorageFake : SecureTokenStorage {
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
