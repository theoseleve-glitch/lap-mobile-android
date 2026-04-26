package com.lap.mobile.auth

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Apple-sign-in-via-Clerk integration shape (T176).
 *
 * Acceptance criterion in the sprint is `⇒ noted, deferred` because
 * (1) Apple sign-in is App-Store-iOS-driven and `Android optional`
 * per the original task spec, and (2) the production runtime depends
 * on Clerk's Sign in with Apple helper which is gated on the Block-I
 * Kotlin 2.3 + AGP 8.9.1 + compileSdk 36 toolchain bump.
 *
 * What this file delivers for the `noted` half of the acceptance:
 *  - The two `fun interface` seams ([AppleAuthProvider] +
 *    [AppleCredentialExchanger]) Clerk's helper plugs into.
 *  - The orchestration kernel that walks
 *    `provider.signIn() → exchanger.exchange() → storage.write()` —
 *    JVM-tested, Hilt-ready, but with no Clerk import on the
 *    classpath today (so it compiles + ships cleanly under the
 *    current toolchain).
 *  - The UI-facing [AppleSignInOutcome] surface that the eventual
 *    LoginScreen "Sign in with Apple" button (alongside T172's
 *    Google button) consumes via `when` for navigation + error
 *    routing.
 *
 * What is `[PENDING: device-required + iOS-driven]`:
 *  - Production [AppleAuthProvider] HTTP/native impl — on iOS goes
 *    through ASAuthorizationAppleIDProvider, on Android goes through
 *    Clerk's Sign-in-with-Apple helper which needs the Block-I
 *    toolchain. Until then, Android impl returns
 *    [AppleSignInResult.ProviderUnavailable] and the UI disables the
 *    button.
 *  - Production [AppleCredentialExchanger] HTTP impl — POSTs the
 *    Apple identity token + authorization code to the backend OR
 *    Clerk for federation; needs the same backend endpoint spec /
 *    Clerk SDK to land.
 *  - LoginScreen wire-up — the Composable adds an "Sign in with
 *    Apple" OutlinedButton next to T172's Google one, calling into
 *    [AppleSignInUseCase.signIn] from the ViewModel.
 *
 * Why this differs from T177 magic-link's two-phase flow: magic-link
 * is request → check email → click link, two distinct UI states. Apple
 * sign-in is one-phase from the user's perspective (tap button → OS
 * native modal → returned). The orchestrator collapses provider +
 * exchange into a single `signIn()` call because the user can't
 * abandon between provider auth and credential exchange — anything
 * mid-flow is a system error not a user choice.
 */
fun interface AppleAuthProvider {
    suspend fun signIn(): AppleSignInResult
}

/**
 * Outcome of asking the OS / Clerk helper to authenticate via Apple.
 *
 * The split between [NetworkFailure] and [ProviderUnavailable]
 * matters specifically for UX:
 *  - [NetworkFailure] means transient connectivity — UI says "try
 *    again later" and re-enables the button.
 *  - [ProviderUnavailable] means the integration isn't loaded at all
 *    (current Block-I-blocked Android state) — UI must DISABLE the
 *    button rather than offer retry, since retry would just hit the
 *    same dead path.
 */
sealed interface AppleSignInResult {
    /** OS / Clerk returned an Apple identity token + authorization code. */
    data class Success(
        val identityToken: String,
        val authorizationCode: String,
    ) : AppleSignInResult

    /** User dismissed the OS native sign-in modal. */
    data object Cancelled : AppleSignInResult

    /** No connectivity to Apple's servers / Clerk's helper. Retry plausible. */
    data object NetworkFailure : AppleSignInResult

    /** Provider integration not available on this device (Android pre-Clerk). */
    data object ProviderUnavailable : AppleSignInResult
}

fun interface AppleCredentialExchanger {
    suspend fun exchange(identityToken: String, authorizationCode: String): AppleExchangeResult
}

/**
 * Outcome of exchanging the Apple identity token + authorization code
 * for our backend's access + refresh pair. Mirrors
 * [com.lap.mobile.auth.RedeemResult] (T179) and
 * [MagicLinkVerifyResult] (T177) since all three end in the same
 * place — a freshly-issued access + refresh pair.
 */
sealed interface AppleExchangeResult {
    /** Backend / Clerk returned new credentials. */
    data class Success(
        val accessToken: String,
        val refreshToken: String,
    ) : AppleExchangeResult

    /** Backend rejected the Apple identity token — common cause is clock skew or key rotation. */
    data object IdentityRejected : AppleExchangeResult

    /** No connectivity / 5xx / malformed response — anything that's "try again later". */
    data object NetworkFailure : AppleExchangeResult
}

/**
 * UI-facing outcome of the Apple sign-in flow. The Composable
 * consumes this in a `when` to drive navigation + button-state +
 * error-message routing.
 */
enum class AppleSignInOutcome {
    /** Tokens stored, ready to navigate to home. */
    Authenticated,

    /** User dismissed the OS sign-in modal — UI re-enables the button, stays on LoginScreen. */
    CancelledByUser,

    /** Network problem at provider OR exchange layer — UI says "try again later". */
    NetworkProblem,

    /** Provider not loaded on this device — UI disables the button. */
    ProviderUnavailable,

    /** Backend rejected the Apple identity token — UI says "sign-in failed, please try again". */
    IdentityRejected,
}

/**
 * Orchestrates Apple sign-in (T176).
 *
 * One-phase from the user's perspective: provider auth + credential
 * exchange happen back-to-back inside `signIn()` because there's no
 * UI state in between — failure modes at either layer collapse into
 * a single [AppleSignInOutcome].
 *
 * On exchange Success, both halves of the credential pair are
 * written before [Authenticated] is returned — same atomicity
 * contract as T177 magic-link + T179 pairing. T174's silent-refresh
 * reads `REFRESH_TOKEN`; writing only access here would force re-auth
 * on the first long-lived 401.
 *
 * On non-Success at any layer, pre-existing tokens are deliberately
 * NOT wiped. A re-sign-in attempt that hits a network blip shouldn't
 * cost the user their already-working session.
 */
@Singleton
class AppleSignInUseCase
@Inject
constructor(
    private val provider: AppleAuthProvider,
    private val exchanger: AppleCredentialExchanger,
    private val storage: SecureTokenStorage,
) {
    suspend fun signIn(): AppleSignInOutcome = when (val result = provider.signIn()) {
        is AppleSignInResult.Success -> exchangeAndStore(result.identityToken, result.authorizationCode)
        AppleSignInResult.Cancelled -> AppleSignInOutcome.CancelledByUser
        AppleSignInResult.NetworkFailure -> AppleSignInOutcome.NetworkProblem
        AppleSignInResult.ProviderUnavailable -> AppleSignInOutcome.ProviderUnavailable
    }

    private suspend fun exchangeAndStore(identityToken: String, authorizationCode: String): AppleSignInOutcome =
        when (val result = exchanger.exchange(identityToken, authorizationCode)) {
            is AppleExchangeResult.Success -> {
                storage.write(SecureTokenKeys.ACCESS_TOKEN, result.accessToken)
                storage.write(SecureTokenKeys.REFRESH_TOKEN, result.refreshToken)
                AppleSignInOutcome.Authenticated
            }
            AppleExchangeResult.IdentityRejected -> AppleSignInOutcome.IdentityRejected
            AppleExchangeResult.NetworkFailure -> AppleSignInOutcome.NetworkProblem
        }
}
