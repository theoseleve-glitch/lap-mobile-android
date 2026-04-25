package com.lap.mobile.auth

import com.lap.mobile.qr.QrPayload
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exchanges a pairing token (from the webapp's QR or T180's manual
 * code-entry fallback) for a fresh access + refresh token pair.
 *
 * Modelled as a `fun interface` so the production HTTP impl —
 * Retrofit-ish POST `/auth/pairing/redeem` with `{ token }` body — can
 * be swapped for an in-test fake without dragging Hilt or
 * MockWebServer into the unit test.
 *
 * The contract is total: any error mode (4xx, 5xx, IOException,
 * malformed body) collapses into one of the explicit
 * [RedeemResult.InvalidToken] / [RedeemResult.NetworkFailure]
 * branches — no exceptions tunnel out, so the use case orchestration
 * doesn't have to wrap calls in try/catch.
 */
fun interface PairingRedeemer {
    suspend fun redeem(pairingToken: String): RedeemResult
}

/**
 * Outcome of a pairing-token exchange. Three shapes only — anything
 * the server can throw at us has to map cleanly into one of these so
 * the UI's `when (outcome)` stays exhaustive.
 *
 * The split between [InvalidToken] and [NetworkFailure] matters: the
 * UX is different. InvalidToken sends the user back to the webapp to
 * regenerate; NetworkFailure tells them to check connectivity and
 * try the same QR again.
 */
sealed interface RedeemResult {
    /** Server returned new credentials. */
    data class Success(
        val accessToken: String,
        val refreshToken: String,
    ) : RedeemResult

    /** Server returned 400/404 — token expired, never valid, or already consumed. */
    data object InvalidToken : RedeemResult

    /** No connectivity, server timeout, 5xx, malformed response — anything that's "try again later". */
    data object NetworkFailure : RedeemResult
}

/**
 * UI-facing outcome of a pairing attempt. The Composable consumes
 * this in a `when` to drive navigation + error message; making the
 * variants explicit (rather than using an `Exception?` or boolean)
 * forces the UI to handle every case rather than fall through
 * silently.
 */
enum class PairingOutcome {
    /** Tokens stored, ready to navigate to home. */
    Authenticated,

    /** Server rejected the token — UI tells user to regenerate the QR in the webapp. */
    TokenRejected,

    /** Network problem — UI tells user to check connectivity and retry the same QR. */
    NetworkProblem,

    /** [QrPayload.Unknown] — UI re-prompts to scan again. */
    UnreadablePayload,
}

/**
 * Orchestrates the pairing flow (T179):
 *   - QR scanned by T178 → [QrPayload]
 *   - Pairing token POSTed via [PairingRedeemer]
 *   - Returned credentials persisted via [SecureTokenStorage] (T173)
 *
 * Both halves of the credential pair are written before [Authenticated]
 * is returned. T174's silent-refresh path reads `REFRESH_TOKEN` to
 * exchange for a new access on 401; if we wrote only access here, the
 * first long-lived-session 401 would force a re-pair via QR.
 *
 * On non-success, pre-existing tokens are deliberately NOT wiped — a
 * re-pair attempt that hits a network blip shouldn't cost the user
 * their already-working session. Only [Success] mutates storage.
 *
 * Production [PairingRedeemer] HTTP impl is [PENDING:
 * device-required] — depends on the backend `/auth/pairing/redeem`
 * endpoint spec. The orchestration locked here works with whatever
 * impl lands there.
 */
@Singleton
class PairingRedeemUseCase
@Inject
constructor(
    private val redeemer: PairingRedeemer,
    private val storage: SecureTokenStorage,
) {
    suspend fun pair(payload: QrPayload): PairingOutcome = when (payload) {
        QrPayload.Unknown -> PairingOutcome.UnreadablePayload
        is QrPayload.PairingToken ->
            when (val result = redeemer.redeem(payload.token)) {
                is RedeemResult.Success -> {
                    storage.write(SecureTokenKeys.ACCESS_TOKEN, result.accessToken)
                    storage.write(SecureTokenKeys.REFRESH_TOKEN, result.refreshToken)
                    PairingOutcome.Authenticated
                }
                RedeemResult.InvalidToken -> PairingOutcome.TokenRejected
                RedeemResult.NetworkFailure -> PairingOutcome.NetworkProblem
            }
    }
}
