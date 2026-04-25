package com.lap.mobile.upload

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject

/**
 * Attaches the Clerk JWT to outgoing requests and reacts to a
 * 401 by refreshing the token and replaying the request once
 * (T149).
 *
 * Policy (encoded in [handle]):
 *   * If [TokenProvider.current] returns a token, add
 *     `Authorization: Bearer <token>` to the request. If it
 *     returns null (anonymous calls — e.g., login), forward
 *     the request without an Authorization header so the
 *     interceptor stays usable for unauthenticated endpoints.
 *   * On a 200/2xx — and on 5xx — do not retry. A 5xx came
 *     back with a valid token, so refreshing wouldn't help.
 *   * On a 401, call [TokenProvider.refresh]:
 *       - if it returns a new token, replay the request once
 *         with the new bearer (close the original response
 *         body so OkHttp doesn't leak it).
 *       - if it returns null (refresh token expired,
 *         network error, etc.), surface the original 401 so
 *         the caller can decide how to react (e.g., bounce
 *         to the login screen).
 *
 * [intercept] is the OkHttp seam; [handle] is the JVM-testable
 * surface that takes a `(Request) -> Response` instead of a
 * full [Interceptor.Chain].
 */
class AuthInterceptor @Inject constructor(
    private val tokenProvider: TokenProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = handle(chain.request()) { chain.proceed(it) }

    fun handle(originalRequest: Request, call: (Request) -> Response): Response {
        val initialToken: String? = tokenProvider.current()
        val firstAttempt: Response = call(originalRequest.withBearer(initialToken))
        if (firstAttempt.code != HTTP_UNAUTHORIZED) {
            return firstAttempt
        }
        val refreshedToken: String = tokenProvider.refresh() ?: return firstAttempt
        firstAttempt.close()
        return call(originalRequest.withBearer(refreshedToken))
    }

    private fun Request.withBearer(token: String?): Request =
        if (token == null) this else newBuilder().header(AUTHORIZATION_HEADER, "Bearer $token").build()

    private companion object {
        const val AUTHORIZATION_HEADER: String = "Authorization"
        const val HTTP_UNAUTHORIZED: Int = 401
    }
}
