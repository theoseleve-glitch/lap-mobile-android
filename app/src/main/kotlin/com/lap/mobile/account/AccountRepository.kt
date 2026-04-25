package com.lap.mobile.account

import com.lap.mobile.auth.SecureTokenKeys
import com.lap.mobile.auth.SecureTokenStorage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Multi-account scaffold (T187) — list-shaped account API backed by
 * single-account semantics in v1.
 *
 * The "not user-facing" acceptance means no UI surfaces this yet.
 * The value is **forward compatibility**: when a future version adds
 * "switch active account" / "add another account", call sites and
 * the repo's API shape don't change — only the impl grows.
 *
 * Derivation source: [SecureTokenStorage] (T173). Presence of
 * [SecureTokenKeys.REFRESH_TOKEN] ⇒ "we're paired to one account".
 * Same predicate T181's
 * [com.lap.mobile.auth.PairedDeviceStateResolver] uses, but
 * exposing an [Account] rather than a UI-pill state. Single source
 * of truth means T175's logout (clears tokens) and T179's pair
 * (writes tokens) automatically flip this — no explicit "set active
 * account" calls anywhere.
 *
 * v1 [AccountId] is the placeholder `"active"` because the real
 * server-side account ID isn't known until T189's `/me` sync.
 * Calling code MUST treat the value as opaque — equality across two
 * paired devices for the same user wouldn't be meaningful with the
 * placeholder. T189 replaces it with the real ID.
 */
@Singleton
class AccountRepository
@Inject
constructor(
    private val storage: SecureTokenStorage,
) {
    fun currentAccount(): Account? {
        val refresh = storage.read(SecureTokenKeys.REFRESH_TOKEN)?.takeIf { it.isNotBlank() }
        return refresh?.let {
            Account(id = ACTIVE_ACCOUNT_PLACEHOLDER_ID, displayLabel = null)
        }
    }

    fun knownAccounts(): List<Account> = listOfNotNull(currentAccount())

    private companion object {
        // v1 single-account placeholder — T189 replaces with the
        // real server-side account ID once /me lands.
        val ACTIVE_ACCOUNT_PLACEHOLDER_ID = AccountId(value = "active")
    }
}
