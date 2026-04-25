package com.lap.mobile.auth

import com.lap.mobile.qr.QrPayload
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val PAIRING_TOKEN: String = "p_01JX9Z8M4KTRP5D6Y3V2N7H8KQ"
private const val NEW_ACCESS_TOKEN: String = "access-issued-on-pair"
private const val NEW_REFRESH_TOKEN: String = "refresh-issued-on-pair"
private const val PRE_EXISTING_ACCESS: String = "stale-access-from-prior-session"
private const val PRE_EXISTING_REFRESH: String = "stale-refresh-from-prior-session"

/**
 * Locks the T179 pairing flow contract: the use case takes a
 * [QrPayload] (scanned by T178), exchanges its token via
 * [PairingRedeemer], and persists the returned credentials in
 * [SecureTokenStorage] (T173) — atomically, so a partial success
 * (access stored but refresh not) can't leave the device in a state
 * where T174's silent-refresh has nothing to exchange.
 *
 * Why a use case rather than dropping the logic into a ViewModel:
 *   - The redeem→store sequence has to be the same whether the
 *     pairing token came from a QR (T178), the manual code-entry
 *     fallback (T180), or the deep-link route (T184). Locking it in
 *     one place means those entry points stay thin.
 *   - The error mapping (network vs invalid-token vs unreadable-QR)
 *     needs to be exhaustive — surfacing a different message per
 *     case is what the UI relies on to route the user (re-scan vs
 *     check connectivity vs ask webapp to regenerate the QR).
 */
class PairingRedeemUseCaseTest {
    @Test
    fun unknown_qr_payload_resolves_to_unreadable_without_calling_redeemer_or_writing_storage() = runTest {
        // User pointed the camera at a Wi-Fi QR or random URL — T178's
        // classifier already filtered it to Unknown. The use case must
        // NOT call the network with empty input or write garbage to
        // storage; the UI re-prompts to scan again.
        val redeemer = RecordingPairingRedeemer(result = RedeemResult.NetworkFailure)
        val storage = PairingTokenStorageFake()
        val useCase = PairingRedeemUseCase(redeemer, storage)

        val outcome = useCase.pair(QrPayload.Unknown)

        assertEquals(PairingOutcome.UnreadablePayload, outcome)
        assertEquals("must not have hit the network on Unknown QR", 0, redeemer.invocationCount)
        assertNull(storage.read(SecureTokenKeys.ACCESS_TOKEN))
        assertNull(storage.read(SecureTokenKeys.REFRESH_TOKEN))
    }

    @Test
    fun successful_redeem_persists_both_access_and_refresh_so_t174_silent_refresh_has_what_it_needs() = runTest {
        // Both halves needed: T174's authenticator reads REFRESH_TOKEN
        // when the access expires. If we wrote only access, the first
        // 401 on a long-lived session would force re-pair via QR.
        val redeemer =
            RecordingPairingRedeemer(
                result = RedeemResult.Success(NEW_ACCESS_TOKEN, NEW_REFRESH_TOKEN),
            )
        val storage = PairingTokenStorageFake()
        val useCase = PairingRedeemUseCase(redeemer, storage)

        useCase.pair(QrPayload.PairingToken(PAIRING_TOKEN))

        assertEquals(NEW_ACCESS_TOKEN, storage.read(SecureTokenKeys.ACCESS_TOKEN))
        assertEquals(NEW_REFRESH_TOKEN, storage.read(SecureTokenKeys.REFRESH_TOKEN))
    }

    @Test
    fun successful_redeem_returns_authenticated_outcome_so_navigation_lands_on_post_login_home() = runTest {
        val redeemer =
            RecordingPairingRedeemer(
                result = RedeemResult.Success(NEW_ACCESS_TOKEN, NEW_REFRESH_TOKEN),
            )
        val useCase = PairingRedeemUseCase(redeemer, PairingTokenStorageFake())

        val outcome = useCase.pair(QrPayload.PairingToken(PAIRING_TOKEN))

        assertEquals(PairingOutcome.Authenticated, outcome)
    }

    @Test
    fun pairing_token_passes_through_to_redeemer_byte_for_byte() {
        runTest {
            // Server-side dedupe and audit logging both rely on the exact
            // token. Trimming, lowercasing, or any other "helpful"
            // normalization here would break replay-debugging.
            val redeemer =
                RecordingPairingRedeemer(
                    result = RedeemResult.Success(NEW_ACCESS_TOKEN, NEW_REFRESH_TOKEN),
                )
            val useCase = PairingRedeemUseCase(redeemer, PairingTokenStorageFake())

            useCase.pair(QrPayload.PairingToken(PAIRING_TOKEN))

            assertEquals(PAIRING_TOKEN, redeemer.lastTokenSeen)
        }
    }

    @Test
    fun invalid_token_response_maps_to_token_rejected_outcome_and_writes_nothing() = runTest {
        // Server returned 400/404 — token expired, never valid, or
        // already consumed. UI tells user to ask webapp to regenerate.
        val redeemer = RecordingPairingRedeemer(result = RedeemResult.InvalidToken)
        val storage = PairingTokenStorageFake()
        val useCase = PairingRedeemUseCase(redeemer, storage)

        val outcome = useCase.pair(QrPayload.PairingToken(PAIRING_TOKEN))

        assertEquals(PairingOutcome.TokenRejected, outcome)
        assertNull(storage.read(SecureTokenKeys.ACCESS_TOKEN))
        assertNull(storage.read(SecureTokenKeys.REFRESH_TOKEN))
    }

    @Test
    fun network_failure_maps_to_network_problem_outcome_and_leaves_pre_existing_tokens_untouched() = runTest {
        // Pre-existing tokens (from a prior session that's still
        // valid) must NOT be wiped just because a re-pair attempt
        // hit a network blip. Otherwise a user re-pairing on a flaky
        // connection would lose their working session.
        val redeemer = RecordingPairingRedeemer(result = RedeemResult.NetworkFailure)
        val storage = PairingTokenStorageFake()
        storage.write(SecureTokenKeys.ACCESS_TOKEN, PRE_EXISTING_ACCESS)
        storage.write(SecureTokenKeys.REFRESH_TOKEN, PRE_EXISTING_REFRESH)
        val useCase = PairingRedeemUseCase(redeemer, storage)

        val outcome = useCase.pair(QrPayload.PairingToken(PAIRING_TOKEN))

        assertEquals(PairingOutcome.NetworkProblem, outcome)
        assertEquals(PRE_EXISTING_ACCESS, storage.read(SecureTokenKeys.ACCESS_TOKEN))
        assertEquals(PRE_EXISTING_REFRESH, storage.read(SecureTokenKeys.REFRESH_TOKEN))
    }

    @Test
    fun successful_redeem_overwrites_pre_existing_tokens_so_re_pair_takes_effect_immediately() = runTest {
        // Re-pair scenario: device was paired to account A, user
        // re-scans a QR for account B. After redeem succeeds, account
        // A's tokens must be gone — otherwise T174's authenticator
        // reads the wrong refresh.
        val redeemer =
            RecordingPairingRedeemer(
                result = RedeemResult.Success(NEW_ACCESS_TOKEN, NEW_REFRESH_TOKEN),
            )
        val storage = PairingTokenStorageFake()
        storage.write(SecureTokenKeys.ACCESS_TOKEN, PRE_EXISTING_ACCESS)
        storage.write(SecureTokenKeys.REFRESH_TOKEN, PRE_EXISTING_REFRESH)
        val useCase = PairingRedeemUseCase(redeemer, storage)

        useCase.pair(QrPayload.PairingToken(PAIRING_TOKEN))

        assertEquals(NEW_ACCESS_TOKEN, storage.read(SecureTokenKeys.ACCESS_TOKEN))
        assertEquals(NEW_REFRESH_TOKEN, storage.read(SecureTokenKeys.REFRESH_TOKEN))
    }
}

private class RecordingPairingRedeemer(
    private val result: RedeemResult,
) : PairingRedeemer {
    var invocationCount: Int = 0
    var lastTokenSeen: String? = null

    override suspend fun redeem(pairingToken: String): RedeemResult {
        invocationCount++
        lastTokenSeen = pairingToken
        return result
    }
}

private class PairingTokenStorageFake : SecureTokenStorage {
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
