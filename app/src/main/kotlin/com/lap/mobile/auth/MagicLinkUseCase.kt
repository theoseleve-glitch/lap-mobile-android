package com.lap.mobile.auth

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Two-phase magic-link sign-in (T177). Symmetric to T179 pairing but
 * split because the magic-link UX is "request → check email → click →
 * verify" rather than the one-shot "scan QR → redeem" pairing flow.
 *
 * The two `fun interface` seams ([MagicLinkSender], [MagicLinkVerifier])
 * are the only network surface — everything else is a pure-Kotlin
 * orchestration that JVM-tests at unit level. Production HTTP impls
 * (POST `/auth/magic-link/send` + POST `/auth/magic-link/verify`) and
 * the email-delivery infrastructure are
 * [PENDING: device-required + backend-required] — landing once
 * backend-sprint locks the endpoint spec OR Block-I toolchain bump
 * unblocks Clerk's magic-link helper.
 *
 * The contract on each seam is total: any error mode (4xx, 5xx,
 * IOException, malformed body) collapses into one of the explicit
 * result branches. No exceptions tunnel through, so the orchestration
 * can map cleanly to UI outcomes without try/catch.
 */
fun interface MagicLinkSender {
    suspend fun send(email: String): MagicLinkSendResult
}

/**
 * Outcome of dispatching a magic link to an email address. Three
 * shapes only — anything the server can throw at us has to map
 * cleanly into one of these so the UI's `when` stays exhaustive.
 *
 * The split between [EmailRejected] and [NetworkFailure] matters
 * because the UX is different: EmailRejected means "fix your typo /
 * try a different address"; NetworkFailure means "check connectivity
 * and retry the same address".
 */
sealed interface MagicLinkSendResult {
    /** Server accepted the request — link is being dispatched to the user's inbox. */
    data object LinkSent : MagicLinkSendResult

    /** Server returned 400/422 — invalid format, blocked address, disposable provider. */
    data object EmailRejected : MagicLinkSendResult

    /** No connectivity, server timeout, 5xx, malformed response — anything that's "try again later". */
    data object NetworkFailure : MagicLinkSendResult
}

fun interface MagicLinkVerifier {
    suspend fun verify(magicToken: String): MagicLinkVerifyResult
}

/**
 * Outcome of verifying a clicked magic link. Mirrors
 * [com.lap.mobile.auth.RedeemResult] (T179) since both end in the
 * same place — a freshly-issued access + refresh pair.
 */
sealed interface MagicLinkVerifyResult {
    /** Server returned new credentials. */
    data class Success(
        val accessToken: String,
        val refreshToken: String,
    ) : MagicLinkVerifyResult

    /** Server returned 400/404 — link expired (typically ~10 min), already consumed, or never valid. */
    data object TokenInvalid : MagicLinkVerifyResult

    /** No connectivity, server timeout, 5xx, malformed response — anything that's "try again later". */
    data object NetworkFailure : MagicLinkVerifyResult
}

/**
 * UI-facing outcome of the request phase. The Composable consumes
 * this in a `when` to drive the next screen (check-email confirmation
 * vs error message). Explicit variants force the UI to handle every
 * case rather than fall through silently.
 */
enum class MagicLinkRequestOutcome {
    /** Server dispatched the link — UI shows "check your email". */
    LinkDispatched,

    /** Server rejected the email address — UI tells user to fix typo / try a different address. */
    EmailRejected,

    /** Network problem — UI tells user to check connectivity and retry. */
    NetworkProblem,

    /** Empty / blank email passed in — UI re-prompts for entry. */
    EmailUnreadable,
}

/**
 * UI-facing outcome of the verify phase. Reached via deep-link
 * (T184), universal App Link (T185), or paste-token fallback.
 */
enum class MagicLinkVerifyOutcome {
    /** Tokens stored, ready to navigate to home. */
    Authenticated,

    /** Server rejected the token — UI tells user to request a fresh link. */
    TokenRejected,

    /** Network problem — UI tells user to check connectivity and retry the same link. */
    NetworkProblem,

    /** Empty / blank token from the deep-link extractor — UI re-prompts. */
    TokenUnreadable,
}

/**
 * Orchestrates the magic-link sign-in flow (T177).
 *
 * **Phase 1 — request:** caller hands an email address; orchestrator
 * forwards to [MagicLinkSender] and returns a UI-facing
 * [MagicLinkRequestOutcome]. Storage is NOT touched here — the
 * credentials only arrive on the verify-phase Success branch, and
 * pre-warming a "pending verification" marker would race against
 * [TokenAuthenticator] (T174).
 *
 * **Phase 2 — verify:** caller hands the magic token (extracted by a
 * deep-link / universal-link route); orchestrator forwards to
 * [MagicLinkVerifier]. On Success both halves of the credential pair
 * are written before [Authenticated] is returned — same atomicity
 * contract as T179 for the same reason (T174's silent-refresh reads
 * `REFRESH_TOKEN`; writing only access would force re-request on the
 * first long-lived 401).
 *
 * On non-success in either phase, pre-existing tokens are deliberately
 * NOT wiped — a verify attempt that hits a network blip shouldn't cost
 * the user their already-working session. Only [Success] mutates
 * storage.
 */
@Singleton
class MagicLinkUseCase
@Inject
constructor(
    private val sender: MagicLinkSender,
    private val verifier: MagicLinkVerifier,
    private val storage: SecureTokenStorage,
) {
    suspend fun requestLink(email: String): MagicLinkRequestOutcome {
        if (email.isBlank()) return MagicLinkRequestOutcome.EmailUnreadable
        return when (sender.send(email)) {
            MagicLinkSendResult.LinkSent -> MagicLinkRequestOutcome.LinkDispatched
            MagicLinkSendResult.EmailRejected -> MagicLinkRequestOutcome.EmailRejected
            MagicLinkSendResult.NetworkFailure -> MagicLinkRequestOutcome.NetworkProblem
        }
    }

    suspend fun verifyLink(magicToken: String): MagicLinkVerifyOutcome {
        if (magicToken.isBlank()) return MagicLinkVerifyOutcome.TokenUnreadable
        return when (val result = verifier.verify(magicToken)) {
            is MagicLinkVerifyResult.Success -> {
                storage.write(SecureTokenKeys.ACCESS_TOKEN, result.accessToken)
                storage.write(SecureTokenKeys.REFRESH_TOKEN, result.refreshToken)
                MagicLinkVerifyOutcome.Authenticated
            }
            MagicLinkVerifyResult.TokenInvalid -> MagicLinkVerifyOutcome.TokenRejected
            MagicLinkVerifyResult.NetworkFailure -> MagicLinkVerifyOutcome.NetworkProblem
        }
    }
}
