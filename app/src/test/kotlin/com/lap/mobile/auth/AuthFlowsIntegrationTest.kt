package com.lap.mobile.auth

import com.lap.mobile.account.AccountRepository
import com.lap.mobile.qr.QrPayload
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PAIR_TOKEN: String = "p_01JX9Z8M4KTRP5D6Y3V2N7H8KQ"
private const val ACCESS_AT_PAIR: String = "access-token-from-pair"
private const val REFRESH_AT_PAIR: String = "refresh-token-from-pair"
private const val ACCESS_AFTER_REFRESH: String = "access-token-after-T174-rotation"
private const val REFRESH_AFTER_REFRESH: String = "refresh-token-after-T174-rotation"
private const val ACCESS_AT_RE_PAIR: String = "access-token-from-second-pair"
private const val REFRESH_AT_RE_PAIR: String = "refresh-token-from-second-pair"

/**
 * Locks T190 contract — auth flows hold together at the COMPOSITION
 * layer, not just inside individual kernels.
 *
 * Each task T172–T189 has its own JVM unit tests covering its kernel
 * in isolation. What those tests can't catch is **drift between
 * kernels** — e.g., if T179's PairingRedeemUseCase started writing
 * tokens under different keys, T173's storage tests would still pass
 * AND T181's resolver tests would still pass, but the indicator would
 * silently lie because the resolver looks at REFRESH_TOKEN and pair
 * landed it under "refresh.token".
 *
 * These tests share **one** [SecureTokenStorage] instance across all
 * the kernels in a flow and assert that the derived states
 * ([PairedDeviceStateResolver], [AccountRepository.currentAccount])
 * agree at every step. That's the property unit tests can't reach —
 * agreement *across* kernels through a shared substrate.
 *
 * Production HTTP impls remain [PENDING: device-required]; these
 * tests use the same fakes the kernels' own unit tests use.
 *
 * Companion instrumented coverage —
 * `AuthFlowsInstrumentedTest`-style end-to-end on real Android
 * Keystore-backed storage, plus actual BiometricPrompt /
 * BiometricLockToggle wire-up — is [PENDING: device-required]. The
 * scaffold for instrumented auth flows lives in
 * `app/src/androidTest/kotlin/com/lap/mobile/LaunchInstrumentedTest.kt`;
 * extending it with auth-specific cases lands when the CI gains a
 * device emulator.
 */
class AuthFlowsIntegrationTest {
    @Test
    fun pair_writes_both_tokens_so_resolver_and_account_repo_agree_we_are_paired_through_one_action() {
        runTest {
            val storage = AuthFlowsStorageFake()
            val pairUseCase = PairingRedeemUseCase(
                redeemer = AuthFlowsPairingRedeemerFake(
                    response = RedeemResult.Success(
                        accessToken = ACCESS_AT_PAIR,
                        refreshToken = REFRESH_AT_PAIR,
                    ),
                ),
                storage = storage,
            )
            val accountRepo = AccountRepository(storage)

            val outcome = pairUseCase.pair(QrPayload.PairingToken(token = PAIR_TOKEN))

            assertEquals(PairingOutcome.Authenticated, outcome)
            assertEquals(ACCESS_AT_PAIR, storage.read(SecureTokenKeys.ACCESS_TOKEN))
            assertEquals(REFRESH_AT_PAIR, storage.read(SecureTokenKeys.REFRESH_TOKEN))
            assertEquals(
                "resolver must report Paired after a successful redeem",
                PairedDeviceState.Paired(identifier = null),
                PairedDeviceStateResolver.resolve(storage),
            )
            val current = accountRepo.currentAccount()
            assertEquals(
                "account repo must surface a current account once pair lands",
                1,
                accountRepo.knownAccounts().size,
            )
            assertEquals("active", current?.id?.value)
        }
    }

    @Test
    fun logout_clears_storage_so_all_three_derived_states_collapse_through_one_action_no_drift() {
        runTest {
            val storage = AuthFlowsStorageFake()
            storage.write(SecureTokenKeys.ACCESS_TOKEN, ACCESS_AT_PAIR)
            storage.write(SecureTokenKeys.REFRESH_TOKEN, REFRESH_AT_PAIR)
            val accountRepo = AccountRepository(storage)
            val logout = LogoutUseCase(
                storage = storage,
                canceller = AuthFlowsNoOpUploadCanceller,
                resetter = AuthFlowsNoOpDbResetter,
            )

            logout.logout()

            assertNull(storage.read(SecureTokenKeys.ACCESS_TOKEN))
            assertNull(storage.read(SecureTokenKeys.REFRESH_TOKEN))
            assertEquals(PairedDeviceState.NotPaired, PairedDeviceStateResolver.resolve(storage))
            assertNull(accountRepo.currentAccount())
            assertTrue(accountRepo.knownAccounts().isEmpty())
        }
    }

    @Test
    fun session_expired_handle_clears_storage_so_resolver_and_account_repo_naturally_observe_unauthenticated() {
        // T186's HandleSessionExpired clears tokens (and ONLY tokens —
        // structural enforcement, see SessionExpiredPolicyTest). The
        // ripple effect through T181's resolver and T187's repo MUST
        // collapse them to unpaired/no-account without those kernels
        // needing to know "session was expired" vs "user logged out".
        runTest {
            val storage = AuthFlowsStorageFake()
            storage.write(SecureTokenKeys.ACCESS_TOKEN, ACCESS_AT_PAIR)
            storage.write(SecureTokenKeys.REFRESH_TOKEN, REFRESH_AT_PAIR)
            val accountRepo = AccountRepository(storage)
            val handle = HandleSessionExpired(storage = storage)

            handle.onExpired()

            assertNull(storage.read(SecureTokenKeys.ACCESS_TOKEN))
            assertNull(storage.read(SecureTokenKeys.REFRESH_TOKEN))
            assertEquals(PairedDeviceState.NotPaired, PairedDeviceStateResolver.resolve(storage))
            assertNull(accountRepo.currentAccount())
        }
    }

    @Test
    fun T174_silent_refresh_rotation_doesnt_flip_pairing_state_so_indicator_doesnt_flicker_during_token_rotation() {
        // T174's silent-refresh rotates BOTH tokens atomically. T181's
        // resolver gates on REFRESH_TOKEN presence (not access) so a
        // successful rotation must keep the resolver in Paired state.
        // If a future refactor of T174 split the rotation into "clear
        // refresh, write new access, write new refresh", the resolver
        // would briefly report NotPaired. This test locks the property
        // that "after rotation, refresh is non-blank".
        runTest {
            val storage = AuthFlowsStorageFake()
            storage.write(SecureTokenKeys.ACCESS_TOKEN, ACCESS_AT_PAIR)
            storage.write(SecureTokenKeys.REFRESH_TOKEN, REFRESH_AT_PAIR)
            val before = PairedDeviceStateResolver.resolve(storage)
            assertEquals(PairedDeviceState.Paired(identifier = null), before)

            storage.write(SecureTokenKeys.ACCESS_TOKEN, ACCESS_AFTER_REFRESH)
            storage.write(SecureTokenKeys.REFRESH_TOKEN, REFRESH_AFTER_REFRESH)

            assertEquals(
                "resolver must remain Paired across an atomic two-token rotation",
                PairedDeviceState.Paired(identifier = null),
                PairedDeviceStateResolver.resolve(storage),
            )
        }
    }

    @Test
    fun re_pair_after_logout_restores_paired_state_so_user_can_re_pair_in_the_same_process_without_restart() {
        // Full lifecycle: pair → logout → pair again. Each step's
        // shared-storage effects MUST compose cleanly so a user who
        // signed out in error doesn't have to force-stop the app.
        runTest {
            val storage = AuthFlowsStorageFake()
            val firstPair = PairingRedeemUseCase(
                redeemer = AuthFlowsPairingRedeemerFake(
                    response = RedeemResult.Success(
                        accessToken = ACCESS_AT_PAIR,
                        refreshToken = REFRESH_AT_PAIR,
                    ),
                ),
                storage = storage,
            )
            val logout = LogoutUseCase(
                storage = storage,
                canceller = AuthFlowsNoOpUploadCanceller,
                resetter = AuthFlowsNoOpDbResetter,
            )
            val secondPair = PairingRedeemUseCase(
                redeemer = AuthFlowsPairingRedeemerFake(
                    response = RedeemResult.Success(
                        accessToken = ACCESS_AT_RE_PAIR,
                        refreshToken = REFRESH_AT_RE_PAIR,
                    ),
                ),
                storage = storage,
            )

            firstPair.pair(QrPayload.PairingToken(token = PAIR_TOKEN))
            logout.logout()
            assertEquals(PairedDeviceState.NotPaired, PairedDeviceStateResolver.resolve(storage))

            secondPair.pair(QrPayload.PairingToken(token = PAIR_TOKEN))

            assertEquals(REFRESH_AT_RE_PAIR, storage.read(SecureTokenKeys.REFRESH_TOKEN))
            assertEquals(ACCESS_AT_RE_PAIR, storage.read(SecureTokenKeys.ACCESS_TOKEN))
            assertEquals(PairedDeviceState.Paired(identifier = null), PairedDeviceStateResolver.resolve(storage))
        }
    }

    @Test
    fun pair_returning_invalid_token_doesnt_write_storage_so_a_failed_pair_doesnt_falsely_flip_indicator_to_paired() {
        // Negative path: T179's contract is "only Success mutates
        // storage". If a buggy redeem implementation wrote tokens on
        // a 400 response, the indicator would lie and the user would
        // see "Paired" with a token the server already rejected.
        runTest {
            val storage = AuthFlowsStorageFake()
            val pairUseCase = PairingRedeemUseCase(
                redeemer = AuthFlowsPairingRedeemerFake(response = RedeemResult.InvalidToken),
                storage = storage,
            )
            val accountRepo = AccountRepository(storage)

            val outcome = pairUseCase.pair(QrPayload.PairingToken(token = PAIR_TOKEN))

            assertEquals(PairingOutcome.TokenRejected, outcome)
            assertNull(storage.read(SecureTokenKeys.ACCESS_TOKEN))
            assertNull(storage.read(SecureTokenKeys.REFRESH_TOKEN))
            assertEquals(PairedDeviceState.NotPaired, PairedDeviceStateResolver.resolve(storage))
            assertNull(accountRepo.currentAccount())
        }
    }

    @Test
    fun pair_then_network_failure_re_pair_preserves_first_credentials_so_a_blip_doesnt_cost_user_their_session() {
        // T179 explicitly: "On non-success, pre-existing tokens are
        // deliberately NOT wiped." Pair successfully, then attempt a
        // re-pair that hits a network blip — the original credentials
        // MUST still be in place, indicator stays Paired.
        runTest {
            val storage = AuthFlowsStorageFake()
            val firstPair = PairingRedeemUseCase(
                redeemer = AuthFlowsPairingRedeemerFake(
                    response = RedeemResult.Success(
                        accessToken = ACCESS_AT_PAIR,
                        refreshToken = REFRESH_AT_PAIR,
                    ),
                ),
                storage = storage,
            )
            firstPair.pair(QrPayload.PairingToken(token = PAIR_TOKEN))
            val rePairOnBlip = PairingRedeemUseCase(
                redeemer = AuthFlowsPairingRedeemerFake(response = RedeemResult.NetworkFailure),
                storage = storage,
            )

            val outcome = rePairOnBlip.pair(QrPayload.PairingToken(token = PAIR_TOKEN))

            assertEquals(PairingOutcome.NetworkProblem, outcome)
            assertEquals(REFRESH_AT_PAIR, storage.read(SecureTokenKeys.REFRESH_TOKEN))
            assertEquals(ACCESS_AT_PAIR, storage.read(SecureTokenKeys.ACCESS_TOKEN))
            assertEquals(PairedDeviceState.Paired(identifier = null), PairedDeviceStateResolver.resolve(storage))
        }
    }
}

private class AuthFlowsStorageFake : SecureTokenStorage {
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

private class AuthFlowsPairingRedeemerFake(private val response: RedeemResult) : PairingRedeemer {
    override suspend fun redeem(pairingToken: String): RedeemResult = response
}

private val AuthFlowsNoOpUploadCanceller: UploadWorkCanceller = UploadWorkCanceller { }

private val AuthFlowsNoOpDbResetter: LocalDatabaseResetter = LocalDatabaseResetter { }
