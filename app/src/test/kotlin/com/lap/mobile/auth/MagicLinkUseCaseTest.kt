package com.lap.mobile.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val USER_EMAIL: String = "racer@example.com"
private const val MAGIC_TOKEN: String = "ml_01JYR8K9D7VHN2P5QF6X3M8C4Z"
private const val NEW_ACCESS_TOKEN: String = "access-issued-on-magic-link-verify"
private const val NEW_REFRESH_TOKEN: String = "refresh-issued-on-magic-link-verify"
private const val PRE_EXISTING_ACCESS: String = "stale-access-from-prior-session"
private const val PRE_EXISTING_REFRESH: String = "stale-refresh-from-prior-session"

/**
 * Locks the T177 magic-link sign-in flow contract — symmetric to T179
 * pairing but two-phase rather than one:
 *
 *  1. **Request phase** — user enters email, app calls
 *     [MagicLinkSender.send]; server dispatches a link to the email.
 *     UI shows "check your email".
 *  2. **Verify phase** — user clicks the link, app's deep-link handler
 *     extracts the token, app calls [MagicLinkVerifier.verify]; server
 *     returns access + refresh, app persists via [SecureTokenStorage]
 *     (T173). UI navigates to home.
 *
 * The two-phase split is what differentiates this from
 * [PairingRedeemUseCase] (T179) where the QR scan delivers the token
 * directly. Magic-link's "check email then click" round-trip means each
 * phase needs its own outcome surface — the request UI shows success
 * differently ("link sent, check inbox") than the verify UI does
 * ("you're in").
 *
 * Why a use case rather than dropping the logic into a ViewModel:
 *   - Each phase can be triggered from multiple entry points — request
 *     from the LoginScreen email input (T172), verify from a deep-link
 *     route (T184) OR a universal App Link (T185) OR a paste-token
 *     fallback. Locking the orchestration in one place keeps those
 *     thin.
 *   - Error mapping is exhaustive — the UI's `when (outcome)` must
 *     route the user differently per case (re-enter email vs check
 *     connectivity vs ask backend for a fresh link).
 */
class MagicLinkUseCaseTest {
    // -------------------------------------------------------------
    // Request phase
    // -------------------------------------------------------------

    @Test
    fun blank_email_resolves_to_unreadable_without_calling_sender() = runTest {
        // Defensive — UI should disable submit on blank, but if a
        // race lets it through (e.g., whitespace-only input that
        // passes a `.isNotEmpty()` check), the use case must NOT
        // POST {"email":""} to the server. Server would 400 and the
        // UX would conflate "you typed nothing" with "your address
        // was rejected".
        val sender = RecordingMagicLinkSender(result = MagicLinkSendResult.LinkSent)
        val useCase = newUseCase(sender = sender)

        val outcome = useCase.requestLink("   ")

        assertEquals(MagicLinkRequestOutcome.EmailUnreadable, outcome)
        assertEquals("must not have hit the network on blank email", 0, sender.invocationCount)
    }

    @Test
    fun successful_send_returns_link_dispatched_so_ui_shows_check_email_state() = runTest {
        val sender = RecordingMagicLinkSender(result = MagicLinkSendResult.LinkSent)
        val useCase = newUseCase(sender = sender)

        val outcome = useCase.requestLink(USER_EMAIL)

        assertEquals(MagicLinkRequestOutcome.LinkDispatched, outcome)
    }

    @Test
    fun email_rejected_response_maps_to_email_rejected_outcome() = runTest {
        // Server says "this address is invalid / blocked / disposable"
        // — distinct from a network blip because the UX is "fix your
        // typo" not "try again later".
        val sender = RecordingMagicLinkSender(result = MagicLinkSendResult.EmailRejected)
        val useCase = newUseCase(sender = sender)

        val outcome = useCase.requestLink(USER_EMAIL)

        assertEquals(MagicLinkRequestOutcome.EmailRejected, outcome)
    }

    @Test
    fun network_failure_during_send_maps_to_network_problem_outcome() = runTest {
        val sender = RecordingMagicLinkSender(result = MagicLinkSendResult.NetworkFailure)
        val useCase = newUseCase(sender = sender)

        val outcome = useCase.requestLink(USER_EMAIL)

        assertEquals(MagicLinkRequestOutcome.NetworkProblem, outcome)
    }

    @Test
    fun email_passes_through_to_sender_byte_for_byte() = runTest {
        // Server-side dedupe (don't dispatch two links 5s apart) and
        // audit logs both rely on the exact email. Lowercasing,
        // trimming, or any other "helpful" normalization here would
        // confuse those.
        val sender = RecordingMagicLinkSender(result = MagicLinkSendResult.LinkSent)
        val useCase = newUseCase(sender = sender)

        useCase.requestLink(USER_EMAIL)

        assertEquals(USER_EMAIL, sender.lastEmailSeen)
    }

    // -------------------------------------------------------------
    // Verify phase
    // -------------------------------------------------------------

    @Test
    fun blank_token_resolves_to_unreadable_without_calling_verifier_or_writing_storage() = runTest {
        // Deep-link / universal-link arrives with `?token=` empty —
        // the route extractor handed us a blank string. Don't POST
        // empty to the server (it would 400 and conflate with
        // "your link was tampered with"); just re-prompt.
        val verifier = RecordingMagicLinkVerifier(
            result = MagicLinkVerifyResult.Success(NEW_ACCESS_TOKEN, NEW_REFRESH_TOKEN),
        )
        val storage = MagicLinkTokenStorageFake()
        val useCase = newUseCase(verifier = verifier, storage = storage)

        val outcome = useCase.verifyLink("")

        assertEquals(MagicLinkVerifyOutcome.TokenUnreadable, outcome)
        assertEquals("must not have hit the network on blank token", 0, verifier.invocationCount)
        assertNull(storage.read(SecureTokenKeys.ACCESS_TOKEN))
        assertNull(storage.read(SecureTokenKeys.REFRESH_TOKEN))
    }

    @Test
    fun successful_verify_persists_both_access_and_refresh_so_t174_silent_refresh_has_what_it_needs() = runTest {
        // Both halves needed — T174 authenticator reads REFRESH_TOKEN
        // when access expires. Writing only access here would force
        // the user to re-request a magic link on the first long-lived
        // session 401.
        val verifier = RecordingMagicLinkVerifier(
            result = MagicLinkVerifyResult.Success(NEW_ACCESS_TOKEN, NEW_REFRESH_TOKEN),
        )
        val storage = MagicLinkTokenStorageFake()
        val useCase = newUseCase(verifier = verifier, storage = storage)

        useCase.verifyLink(MAGIC_TOKEN)

        assertEquals(NEW_ACCESS_TOKEN, storage.read(SecureTokenKeys.ACCESS_TOKEN))
        assertEquals(NEW_REFRESH_TOKEN, storage.read(SecureTokenKeys.REFRESH_TOKEN))
    }

    @Test
    fun successful_verify_returns_authenticated_outcome_so_navigation_lands_on_post_login_home() = runTest {
        val verifier = RecordingMagicLinkVerifier(
            result = MagicLinkVerifyResult.Success(NEW_ACCESS_TOKEN, NEW_REFRESH_TOKEN),
        )
        val useCase = newUseCase(verifier = verifier)

        val outcome = useCase.verifyLink(MAGIC_TOKEN)

        assertEquals(MagicLinkVerifyOutcome.Authenticated, outcome)
    }

    @Test
    fun magic_token_passes_through_to_verifier_byte_for_byte() = runTest {
        // Magic tokens are server-issued one-time signatures with
        // exact-match server-side validation. Any normalization here
        // would invalidate every link.
        val verifier = RecordingMagicLinkVerifier(
            result = MagicLinkVerifyResult.Success(NEW_ACCESS_TOKEN, NEW_REFRESH_TOKEN),
        )
        val useCase = newUseCase(verifier = verifier)

        useCase.verifyLink(MAGIC_TOKEN)

        assertEquals(MAGIC_TOKEN, verifier.lastTokenSeen)
    }

    @Test
    fun token_invalid_response_maps_to_token_rejected_and_writes_nothing() = runTest {
        // Server returned 400/404 — token expired (links typically
        // good for ~10 min), already consumed (one-time use), or
        // never valid. UI tells user to request a fresh link.
        val verifier = RecordingMagicLinkVerifier(result = MagicLinkVerifyResult.TokenInvalid)
        val storage = MagicLinkTokenStorageFake()
        val useCase = newUseCase(verifier = verifier, storage = storage)

        val outcome = useCase.verifyLink(MAGIC_TOKEN)

        assertEquals(MagicLinkVerifyOutcome.TokenRejected, outcome)
        assertNull(storage.read(SecureTokenKeys.ACCESS_TOKEN))
        assertNull(storage.read(SecureTokenKeys.REFRESH_TOKEN))
    }

    @Test
    fun verify_network_failure_leaves_pre_existing_tokens_untouched() = runTest {
        // Pre-existing tokens (from a prior session) must NOT be
        // wiped because a verify attempt hit a network blip.
        // Otherwise a user clicking a link on a flaky connection
        // would lose their working session for no good reason.
        val verifier = RecordingMagicLinkVerifier(result = MagicLinkVerifyResult.NetworkFailure)
        val storage = MagicLinkTokenStorageFake()
        storage.write(SecureTokenKeys.ACCESS_TOKEN, PRE_EXISTING_ACCESS)
        storage.write(SecureTokenKeys.REFRESH_TOKEN, PRE_EXISTING_REFRESH)
        val useCase = newUseCase(verifier = verifier, storage = storage)

        val outcome = useCase.verifyLink(MAGIC_TOKEN)

        assertEquals(MagicLinkVerifyOutcome.NetworkProblem, outcome)
        assertEquals(PRE_EXISTING_ACCESS, storage.read(SecureTokenKeys.ACCESS_TOKEN))
        assertEquals(PRE_EXISTING_REFRESH, storage.read(SecureTokenKeys.REFRESH_TOKEN))
    }

    @Test
    fun successful_verify_overwrites_pre_existing_tokens_so_re_auth_takes_effect_immediately() = runTest {
        // Re-auth scenario: device logged in as account A, user
        // clicks a magic link issued to account B. After verify
        // succeeds, account A's tokens must be gone — otherwise
        // T174's authenticator reads the wrong refresh.
        val verifier = RecordingMagicLinkVerifier(
            result = MagicLinkVerifyResult.Success(NEW_ACCESS_TOKEN, NEW_REFRESH_TOKEN),
        )
        val storage = MagicLinkTokenStorageFake()
        storage.write(SecureTokenKeys.ACCESS_TOKEN, PRE_EXISTING_ACCESS)
        storage.write(SecureTokenKeys.REFRESH_TOKEN, PRE_EXISTING_REFRESH)
        val useCase = newUseCase(verifier = verifier, storage = storage)

        useCase.verifyLink(MAGIC_TOKEN)

        assertEquals(NEW_ACCESS_TOKEN, storage.read(SecureTokenKeys.ACCESS_TOKEN))
        assertEquals(NEW_REFRESH_TOKEN, storage.read(SecureTokenKeys.REFRESH_TOKEN))
    }

    @Test
    fun request_phase_does_not_touch_secure_token_storage() = runTest {
        // Send phase has no business writing tokens — the actual
        // credentials only arrive on the verify-phase Success branch.
        // Locks against a future "let's pre-warm the store with a
        // pending-verification marker" refactor that would race
        // against T174's authenticator.
        val sender = RecordingMagicLinkSender(result = MagicLinkSendResult.LinkSent)
        val storage = MagicLinkTokenStorageFake()
        storage.write(SecureTokenKeys.ACCESS_TOKEN, PRE_EXISTING_ACCESS)
        val useCase = newUseCase(sender = sender, storage = storage)

        useCase.requestLink(USER_EMAIL)

        assertEquals(PRE_EXISTING_ACCESS, storage.read(SecureTokenKeys.ACCESS_TOKEN))
    }

    private fun newUseCase(
        sender: MagicLinkSender = RecordingMagicLinkSender(result = MagicLinkSendResult.LinkSent),
        verifier: MagicLinkVerifier = RecordingMagicLinkVerifier(
            result = MagicLinkVerifyResult.Success(NEW_ACCESS_TOKEN, NEW_REFRESH_TOKEN),
        ),
        storage: SecureTokenStorage = MagicLinkTokenStorageFake(),
    ): MagicLinkUseCase = MagicLinkUseCase(sender, verifier, storage)
}

private class RecordingMagicLinkSender(
    private val result: MagicLinkSendResult,
) : MagicLinkSender {
    var invocationCount: Int = 0
    var lastEmailSeen: String? = null

    override suspend fun send(email: String): MagicLinkSendResult {
        invocationCount++
        lastEmailSeen = email
        return result
    }
}

private class RecordingMagicLinkVerifier(
    private val result: MagicLinkVerifyResult,
) : MagicLinkVerifier {
    var invocationCount: Int = 0
    var lastTokenSeen: String? = null

    override suspend fun verify(magicToken: String): MagicLinkVerifyResult {
        invocationCount++
        lastTokenSeen = magicToken
        return result
    }
}

private class MagicLinkTokenStorageFake : SecureTokenStorage {
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
