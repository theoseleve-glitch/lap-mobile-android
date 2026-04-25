package com.lap.mobile.auth

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

private const val AUTHORIZATION_HEADER: String = "Authorization"

/**
 * OkHttp [Authenticator] that performs silent token refresh on 401
 * responses (T174).
 *
 * Decision tree on each 401:
 *   1. If [Response.priorResponse] is non-null, this Response is itself
 *      a retry — the refreshed token also got 401'd. Returning null
 *      here breaks the refresh-retry-401 loop and lets OkHttp surface
 *      the 401 to the caller, which routes the user to login.
 *   2. Read the refresh token from [SecureTokenStorage]. If missing or
 *      blank, return null — there's nothing to exchange.
 *   3. Call [TokenRefresher.refresh]. On [RefreshResult.Success],
 *      persist the rotated pair and rebuild the original request with
 *      the fresh `Authorization: Bearer …` header. On
 *      [RefreshResult.Failure], clear both tokens (the refresh token
 *      is dead — keeping the stale access around would only re-trigger
 *      this code path on the next request) and return null.
 *
 * Distinct from [com.lap.mobile.upload.AuthInterceptor] (T149): the
 * Interceptor attaches the bearer on outbound requests and is in the
 * happy-path hot loop; the Authenticator only fires when OkHttp itself
 * has gotten a 401 back. Using both is the canonical OkHttp pattern.
 *
 * Wiring the production [TokenRefresher] (which calls Clerk or our
 * `/auth/refresh` endpoint) is [PENDING: device-required] for the live
 * integration end of T174 — the Authenticator's logic itself is
 * exhaustively covered by JVM tests in TokenAuthenticatorTest.
 */
@Singleton
class TokenAuthenticator
@Inject
constructor(
    private val storage: SecureTokenStorage,
    private val refresher: TokenRefresher,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) {
            return null
        }
        val refreshToken = storage.read(SecureTokenKeys.REFRESH_TOKEN)?.takeIf { it.isNotBlank() }
            ?: return null
        return when (val result = refresher.refresh(refreshToken)) {
            is RefreshResult.Success -> {
                storage.write(SecureTokenKeys.ACCESS_TOKEN, result.newAccessToken)
                storage.write(SecureTokenKeys.REFRESH_TOKEN, result.newRefreshToken)
                response.request
                    .newBuilder()
                    .header(AUTHORIZATION_HEADER, "Bearer ${result.newAccessToken}")
                    .build()
            }
            RefreshResult.Failure -> {
                storage.clear(SecureTokenKeys.ACCESS_TOKEN)
                storage.clear(SecureTokenKeys.REFRESH_TOKEN)
                null
            }
        }
    }
}
