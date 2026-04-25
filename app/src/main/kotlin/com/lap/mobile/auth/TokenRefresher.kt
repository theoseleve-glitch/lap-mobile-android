package com.lap.mobile.auth

/**
 * Exchanges a refresh token for a fresh access + refresh token pair.
 *
 * Modeled as a `fun interface` (single function, no state) so the
 * production HTTP impl can be swapped for an in-test stub without
 * dragging Hilt through every test.
 *
 * Synchronous on purpose: OkHttp's [okhttp3.Authenticator.authenticate]
 * is synchronous and runs on the OkHttp dispatcher thread. The
 * production refresher uses OkHttp's blocking `Call.execute()`, which
 * matches that contract — wrapping a `suspend` refresher in
 * `runBlocking` here would just re-introduce the same blocking with
 * extra coroutine overhead.
 *
 * The contract is total in the sense that any error condition
 * (HTTP 4xx/5xx, IOException, malformed body) collapses into
 * [RefreshResult.Failure] — the caller ([TokenAuthenticator]) decides
 * what to do with that, and we don't want exceptions tunneling out of
 * Authenticator (OkHttp would treat them as a connection failure and
 * retry the original endpoint, which is wrong here).
 */
fun interface TokenRefresher {
    fun refresh(refreshToken: String): RefreshResult
}

/**
 * Outcome of a token refresh attempt. The success branch carries
 * **both** halves: the server is expected to rotate the refresh token
 * on every refresh (one-time-use semantics per OAuth 2.0 best
 * practice — RFC 6749 §10.4), so persisting only the new access
 * token would burn the refresh token without recording the rotation.
 */
sealed interface RefreshResult {
    data class Success(
        val newAccessToken: String,
        val newRefreshToken: String,
    ) : RefreshResult

    data object Failure : RefreshResult
}
