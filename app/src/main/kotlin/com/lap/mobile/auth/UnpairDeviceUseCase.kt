package com.lap.mobile.auth

import javax.inject.Inject
import javax.inject.Singleton

/**
 * POSTs to `/auth/devices/:id/revoke` to mark this device revoked
 * server-side (T182).
 *
 * Modelled as a `fun interface` mirroring [TokenRefresher] (T174) and
 * [PairingRedeemer] (T179): synchronous return type collapsing every
 * error mode (4xx, 5xx, IOException, malformed body) into one of the
 * explicit [RevocationResult] branches. Exceptions don't tunnel out
 * so the use case orchestration doesn't need try/catch wrappers.
 *
 * Production HTTP impl is [PENDING: device-required + backend-
 * required] — depends on the backend `/auth/devices/:id/revoke`
 * endpoint spec landing.
 */
fun interface DeviceRevoker {
    suspend fun revokeCurrentDevice(): RevocationResult
}

/**
 * Outcome of a single revoke call. Three shapes — anything else the
 * server can throw at us has to fold into one of these so the use
 * case's `when` stays exhaustive.
 *
 * The split between [Unauthorized] and [NetworkFailure] matters
 * because the user-facing message differs: 401 means "your session
 * was already invalid server-side, we cleaned up locally" (no
 * action needed); NetworkFailure means "we couldn't reach the
 * server — your device may still appear active there until the
 * refresh token expires naturally" (informational warning).
 */
sealed interface RevocationResult {
    /** Server acknowledged the revoke. */
    data object Success : RevocationResult

    /** 401 — token was already revoked or expired server-side. */
    data object Unauthorized : RevocationResult

    /** No connectivity / 5xx / malformed response — anything that's "couldn't reach server". */
    data object NetworkFailure : RevocationResult
}

/**
 * UI-facing outcome of an unpair attempt. Three variants because
 * each gets a slightly different message in Settings:
 *
 *   - [UnpairedAndRevoked]: "This device has been signed out and
 *     unlinked from your account."
 *   - [UnpairedAlreadyUnauthorized]: "This device has been signed
 *     out." (Server-side it was already revoked; we just caught up.)
 *   - [UnpairedServerUnreachable]: "This device has been signed out
 *     locally. It may still appear active on lap-coach.racing for a
 *     short time until the connection refreshes."
 */
enum class UnpairOutcome {
    UnpairedAndRevoked,
    UnpairedAlreadyUnauthorized,
    UnpairedServerUnreachable,
}

/**
 * Orchestrates the "Unpair this device" flow (T182):
 *   1. POST /auth/devices/:id/revoke (via [DeviceRevoker])
 *   2. Run local logout (delegated to T175's [LogoutUseCase])
 *
 * Order is locked: revoke FIRST, logout SECOND. The revoke call
 * needs the soon-to-be-dead bearer in its Authorization header; if
 * logout ran first, storage would already be wiped and the revoke
 * call would go out unauthenticated (or fail to be made at all
 * depending on how the network layer resolves the token).
 *
 * Local logout runs REGARDLESS of the revoke result. The user
 * tapped "unpair this device"; if the server is unreachable, we
 * still respect the intent locally. The next /auth/refresh would
 * 401 anyway, so the user effectively IS unpaired — failing to log
 * out locally would just trap them in a state they tried to leave.
 *
 * Composing [LogoutUseCase] (rather than re-implementing the
 * orchestration) inherits T175's locked invariants (cancel-uploads-
 * before-tokens-cleared, atomic clear of both halves). Any future
 * change to logout semantics flows through one place.
 */
@Singleton
class UnpairDeviceUseCase
@Inject
constructor(
    private val revoker: DeviceRevoker,
    private val logout: LogoutUseCase,
) {
    suspend fun unpair(): UnpairOutcome {
        val result = revoker.revokeCurrentDevice()
        logout.logout()
        return when (result) {
            RevocationResult.Success -> UnpairOutcome.UnpairedAndRevoked
            RevocationResult.Unauthorized -> UnpairOutcome.UnpairedAlreadyUnauthorized
            RevocationResult.NetworkFailure -> UnpairOutcome.UnpairedServerUnreachable
        }
    }
}
