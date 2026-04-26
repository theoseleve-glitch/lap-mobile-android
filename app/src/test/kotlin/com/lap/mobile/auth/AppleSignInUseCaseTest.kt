package com.lap.mobile.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val APPLE_IDENTITY_TOKEN: String = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjUwMzVjOWJ6ZGsifQ.faux"
private const val APPLE_AUTH_CODE: String = "c0a1b2c3d4e5f6g7h8i9-applefederationauthcode"
private const val NEW_ACCESS_TOKEN: String = "access-issued-on-apple-exchange"
private const val NEW_REFRESH_TOKEN: String = "refresh-issued-on-apple-exchange"
private const val PRE_EXISTING_ACCESS: String = "stale-access-from-prior-session"
private const val PRE_EXISTING_REFRESH: String = "stale-refresh-from-prior-session"

/**
 * Locks the T176 Apple-sign-in-via-Clerk integration-shape contract.
 *
 * T176's acceptance criterion is `⇒ noted, deferred` — the acceptance
 * is *the integration shape exists with a JVM contract* rather than a
 * fully wired runtime. The two `fun interface` seams
 * ([AppleAuthProvider] and [AppleCredentialExchanger]) are the seams
 * Clerk's Apple sign-in helper will plug into when Block-I toolchain
 * bump unblocks the SDK; until then, the orchestration contract
 * locked here works with whatever impl lands.
 *
 * Why this differs from T177 magic-link's two-phase flow:
 *  - Magic-link is two-phase: user enters email → check email → click
 *    link (request + verify). Apple sign-in is one-phase from the
 *    user's perspective: tap "Sign in with Apple" → OS native modal →
 *    return identity token. The orchestrator collapses provider +
 *    exchange into a single `signIn()` call because the user can't
 *    abandon between provider auth and credential exchange.
 *
 * Why the [ProviderUnavailable] outcome is distinct from
 * [NetworkProblem]:
 *  - Android with no Clerk SDK loaded (the current Block-I-blocked
 *    state) genuinely cannot run Sign in with Apple natively — the
 *    OS doesn't speak the Apple federation protocol the way iOS does,
 *    and a webflow workaround needs an OAuth client id we haven't
 *    registered. The UI needs to disable the button rather than show
 *    "try again later" (which would imply transient failure).
 *  - The `Android optional` qualifier in the original task spec means
 *    this surface can validly return ProviderUnavailable on Android
 *    today; iOS via Clerk is the canonical path.
 */
class AppleSignInUseCaseTest {
    @Test
    fun provider_success_with_exchange_success_returns_authenticated_and_persists_both_tokens() = runTest {
        val provider = RecordingAppleAuthProvider(
            result = AppleSignInResult.Success(APPLE_IDENTITY_TOKEN, APPLE_AUTH_CODE),
        )
        val exchanger = RecordingAppleCredentialExchanger(
            result = AppleExchangeResult.Success(NEW_ACCESS_TOKEN, NEW_REFRESH_TOKEN),
        )
        val storage = AppleTokenStorageFake()
        val useCase = AppleSignInUseCase(provider, exchanger, storage)

        val outcome = useCase.signIn()

        assertEquals(AppleSignInOutcome.Authenticated, outcome)
        assertEquals(NEW_ACCESS_TOKEN, storage.read(SecureTokenKeys.ACCESS_TOKEN))
        assertEquals(NEW_REFRESH_TOKEN, storage.read(SecureTokenKeys.REFRESH_TOKEN))
    }

    @Test
    fun provider_success_with_exchange_success_overwrites_pre_existing_tokens() = runTest {
        // Re-auth scenario: device logged in as account A, user signs
        // in via Apple on a different account B. Account A's tokens
        // must be gone after exchange — otherwise T174's authenticator
        // reads the wrong refresh.
        val provider = RecordingAppleAuthProvider(
            result = AppleSignInResult.Success(APPLE_IDENTITY_TOKEN, APPLE_AUTH_CODE),
        )
        val exchanger = RecordingAppleCredentialExchanger(
            result = AppleExchangeResult.Success(NEW_ACCESS_TOKEN, NEW_REFRESH_TOKEN),
        )
        val storage = AppleTokenStorageFake()
        storage.write(SecureTokenKeys.ACCESS_TOKEN, PRE_EXISTING_ACCESS)
        storage.write(SecureTokenKeys.REFRESH_TOKEN, PRE_EXISTING_REFRESH)
        val useCase = AppleSignInUseCase(provider, exchanger, storage)

        useCase.signIn()

        assertEquals(NEW_ACCESS_TOKEN, storage.read(SecureTokenKeys.ACCESS_TOKEN))
        assertEquals(NEW_REFRESH_TOKEN, storage.read(SecureTokenKeys.REFRESH_TOKEN))
    }

    @Test
    fun exchange_identity_rejected_returns_identity_rejected_outcome_writes_nothing() = runTest {
        // Server says the Apple identity token failed validation —
        // common cause is clock skew between device and server, or
        // the Apple-side public key rotation hasn't propagated.
        // Distinct from cancellation (UI re-enables the button) and
        // distinct from network (UI says "try again later").
        val provider = RecordingAppleAuthProvider(
            result = AppleSignInResult.Success(APPLE_IDENTITY_TOKEN, APPLE_AUTH_CODE),
        )
        val exchanger = RecordingAppleCredentialExchanger(result = AppleExchangeResult.IdentityRejected)
        val storage = AppleTokenStorageFake()
        val useCase = AppleSignInUseCase(provider, exchanger, storage)

        val outcome = useCase.signIn()

        assertEquals(AppleSignInOutcome.IdentityRejected, outcome)
        assertNull(storage.read(SecureTokenKeys.ACCESS_TOKEN))
        assertNull(storage.read(SecureTokenKeys.REFRESH_TOKEN))
    }

    @Test
    fun exchange_network_failure_returns_network_problem_and_leaves_pre_existing_tokens_untouched() = runTest {
        // Same atomicity contract as T177 magic-link: pre-existing
        // tokens (from a prior session) must NOT be wiped because a
        // re-sign-in via Apple hit a network blip. Otherwise a user
        // re-auth on a flaky connection would lose their working
        // session.
        val provider = RecordingAppleAuthProvider(
            result = AppleSignInResult.Success(APPLE_IDENTITY_TOKEN, APPLE_AUTH_CODE),
        )
        val exchanger = RecordingAppleCredentialExchanger(result = AppleExchangeResult.NetworkFailure)
        val storage = AppleTokenStorageFake()
        storage.write(SecureTokenKeys.ACCESS_TOKEN, PRE_EXISTING_ACCESS)
        storage.write(SecureTokenKeys.REFRESH_TOKEN, PRE_EXISTING_REFRESH)
        val useCase = AppleSignInUseCase(provider, exchanger, storage)

        val outcome = useCase.signIn()

        assertEquals(AppleSignInOutcome.NetworkProblem, outcome)
        assertEquals(PRE_EXISTING_ACCESS, storage.read(SecureTokenKeys.ACCESS_TOKEN))
        assertEquals(PRE_EXISTING_REFRESH, storage.read(SecureTokenKeys.REFRESH_TOKEN))
    }

    @Test
    fun provider_cancelled_returns_cancelled_by_user_without_calling_exchanger_or_writing_storage() = runTest {
        // User dismissed the OS native Apple sign-in modal. UI re-enables
        // the button and stays on LoginScreen. We must NOT have called
        // the exchange (no auth code to exchange) or touched storage.
        val provider = RecordingAppleAuthProvider(result = AppleSignInResult.Cancelled)
        val exchanger = RecordingAppleCredentialExchanger(
            result = AppleExchangeResult.Success(NEW_ACCESS_TOKEN, NEW_REFRESH_TOKEN),
        )
        val storage = AppleTokenStorageFake()
        val useCase = AppleSignInUseCase(provider, exchanger, storage)

        val outcome = useCase.signIn()

        assertEquals(AppleSignInOutcome.CancelledByUser, outcome)
        assertEquals("must not have hit the network on cancellation", 0, exchanger.invocationCount)
        assertNull(storage.read(SecureTokenKeys.ACCESS_TOKEN))
        assertNull(storage.read(SecureTokenKeys.REFRESH_TOKEN))
    }

    @Test
    fun provider_network_failure_returns_network_problem_without_calling_exchanger() = runTest {
        // Provider couldn't even reach Apple's servers — exchanger
        // wouldn't have anything to send anyway, must short-circuit.
        val provider = RecordingAppleAuthProvider(result = AppleSignInResult.NetworkFailure)
        val exchanger = RecordingAppleCredentialExchanger(
            result = AppleExchangeResult.Success(NEW_ACCESS_TOKEN, NEW_REFRESH_TOKEN),
        )
        val storage = AppleTokenStorageFake()
        val useCase = AppleSignInUseCase(provider, exchanger, storage)

        val outcome = useCase.signIn()

        assertEquals(AppleSignInOutcome.NetworkProblem, outcome)
        assertEquals("must not have hit the exchanger on provider network failure", 0, exchanger.invocationCount)
    }

    @Test
    fun provider_unavailable_returns_provider_unavailable_distinct_from_network_problem() = runTest {
        // Android with no Clerk SDK loaded returns Unavailable. UI
        // needs to DISABLE the button rather than say "try again
        // later" (which would imply transient failure). The variant
        // split matters specifically for this UX distinction.
        val provider = RecordingAppleAuthProvider(result = AppleSignInResult.ProviderUnavailable)
        val exchanger = RecordingAppleCredentialExchanger(
            result = AppleExchangeResult.Success(NEW_ACCESS_TOKEN, NEW_REFRESH_TOKEN),
        )
        val useCase = AppleSignInUseCase(provider, exchanger, AppleTokenStorageFake())

        val outcome = useCase.signIn()

        assertEquals(AppleSignInOutcome.ProviderUnavailable, outcome)
        assertEquals("must not have hit the exchanger on provider unavailable", 0, exchanger.invocationCount)
    }

    @Test
    fun identity_token_and_authorization_code_pass_through_to_exchanger_byte_for_byte() = runTest {
        // Apple identity token is a JWT signed by Apple — server-side
        // validation is exact-match against Apple's public key. Any
        // normalization (trim, base64 re-encode) here would invalidate
        // every sign-in. Same contract as T179 pairing token + T177
        // magic-link token.
        val provider = RecordingAppleAuthProvider(
            result = AppleSignInResult.Success(APPLE_IDENTITY_TOKEN, APPLE_AUTH_CODE),
        )
        val exchanger = RecordingAppleCredentialExchanger(
            result = AppleExchangeResult.Success(NEW_ACCESS_TOKEN, NEW_REFRESH_TOKEN),
        )
        val useCase = AppleSignInUseCase(provider, exchanger, AppleTokenStorageFake())

        useCase.signIn()

        assertEquals(APPLE_IDENTITY_TOKEN, exchanger.lastIdentityTokenSeen)
        assertEquals(APPLE_AUTH_CODE, exchanger.lastAuthorizationCodeSeen)
    }
}

private class RecordingAppleAuthProvider(
    private val result: AppleSignInResult,
) : AppleAuthProvider {
    var invocationCount: Int = 0

    override suspend fun signIn(): AppleSignInResult {
        invocationCount++
        return result
    }
}

private class RecordingAppleCredentialExchanger(
    private val result: AppleExchangeResult,
) : AppleCredentialExchanger {
    var invocationCount: Int = 0
    var lastIdentityTokenSeen: String? = null
    var lastAuthorizationCodeSeen: String? = null

    override suspend fun exchange(identityToken: String, authorizationCode: String): AppleExchangeResult {
        invocationCount++
        lastIdentityTokenSeen = identityToken
        lastAuthorizationCodeSeen = authorizationCode
        return result
    }
}

private class AppleTokenStorageFake : SecureTokenStorage {
    private val store: MutableMap<String, String> = mutableMapOf()

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
