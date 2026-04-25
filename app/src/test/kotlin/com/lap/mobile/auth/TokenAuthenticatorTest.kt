package com.lap.mobile.auth

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private const val INITIAL_ACCESS: String = "access-v1"
private const val INITIAL_REFRESH: String = "refresh-v1"
private const val ROTATED_ACCESS: String = "access-v2"
private const val ROTATED_REFRESH: String = "refresh-v2"
private const val URL: String = "https://ingest.example/api/v1/sessions/ingest"
private const val TEXT_PLAIN: String = "text/plain"
private const val UNAUTHORIZED: Int = 401

/**
 * Locks T174 contract: silent token refresh on 401 via OkHttp's
 * [okhttp3.Authenticator] seam — distinct from T149's
 * [com.lap.mobile.upload.AuthInterceptor] (which attaches the bearer on
 * outbound requests). Authenticator gives us OkHttp's built-in
 * single-retry semantics for free; the Interceptor stays in charge of
 * attaching the (possibly newly-rotated) access token.
 *
 * Why two seams: the Interceptor sees every request and must know the
 * "current" access token; the Authenticator only fires when OkHttp
 * itself has gotten a 401 back, and is the canonical place to swap a
 * dead access token for a fresh one. Using both is the documented OkHttp
 * pattern (https://square.github.io/okhttp/3.x/okhttp/okhttp3/Authenticator.html).
 */
class TokenAuthenticatorTest {
    @Test
    fun no_refresh_token_in_storage_returns_null_so_okhttp_propagates_the_401_to_the_caller() {
        // First-launch / signed-out state. The 401 means the request hit
        // an authed endpoint without credentials. We can't refresh
        // because there's no refresh token — bouncing the user to login
        // is the correct UX, so we return null.
        val storage = AuthenticatorTokenStorageFake()
        val refresher = RecordingTokenRefresher(result = RefreshResult.Failure)
        val authenticator = TokenAuthenticator(storage, refresher)

        val replay = authenticator.authenticate(route = null, response = unauthorizedResponse())

        assertNull(replay)
        assertEquals("must not have called refresher when no refresh token exists", 0, refresher.invocationCount)
    }

    @Test
    fun blank_refresh_token_is_treated_as_missing_so_a_corrupted_storage_value_doesnt_round_trip_an_empty_string() {
        // Defensive: if a write somehow landed an empty string in the
        // refresh slot (race during logout, partial write), don't POST it
        // to /auth/refresh — the server would 400 and we'd leak the bug.
        val storage = AuthenticatorTokenStorageFake()
        storage.write(SecureTokenKeys.REFRESH_TOKEN, "   ")
        val refresher = RecordingTokenRefresher(result = RefreshResult.Failure)
        val authenticator = TokenAuthenticator(storage, refresher)

        val replay = authenticator.authenticate(route = null, response = unauthorizedResponse())

        assertNull(replay)
        assertEquals(0, refresher.invocationCount)
    }

    @Test
    fun successful_refresh_replays_request_with_the_newly_rotated_bearer() {
        val storage = AuthenticatorTokenStorageFake()
        storage.write(SecureTokenKeys.ACCESS_TOKEN, INITIAL_ACCESS)
        storage.write(SecureTokenKeys.REFRESH_TOKEN, INITIAL_REFRESH)
        val refresher =
            RecordingTokenRefresher(
                result = RefreshResult.Success(newAccessToken = ROTATED_ACCESS, newRefreshToken = ROTATED_REFRESH),
            )
        val authenticator = TokenAuthenticator(storage, refresher)

        val replay = authenticator.authenticate(route = null, response = unauthorizedResponse())

        assertNotNull(replay)
        assertEquals("Bearer $ROTATED_ACCESS", replay!!.header("Authorization"))
    }

    @Test
    fun successful_refresh_writes_both_new_access_and_new_refresh_so_token_rotation_doesnt_leak_the_old_pair() {
        // Token rotation: the server's /auth/refresh response includes a
        // fresh refresh token (one-time-use rotation per OAuth best
        // practice). If we wrote only the access half, the next 401
        // would replay the now-burned old refresh and fail.
        val storage = AuthenticatorTokenStorageFake()
        storage.write(SecureTokenKeys.ACCESS_TOKEN, INITIAL_ACCESS)
        storage.write(SecureTokenKeys.REFRESH_TOKEN, INITIAL_REFRESH)
        val refresher =
            RecordingTokenRefresher(
                result = RefreshResult.Success(newAccessToken = ROTATED_ACCESS, newRefreshToken = ROTATED_REFRESH),
            )
        val authenticator = TokenAuthenticator(storage, refresher)

        authenticator.authenticate(route = null, response = unauthorizedResponse())

        assertEquals(ROTATED_ACCESS, storage.read(SecureTokenKeys.ACCESS_TOKEN))
        assertEquals(ROTATED_REFRESH, storage.read(SecureTokenKeys.REFRESH_TOKEN))
    }

    @Test
    fun successful_refresh_passes_the_stored_refresh_token_to_the_refresher_byte_for_byte() {
        val storage = AuthenticatorTokenStorageFake()
        storage.write(SecureTokenKeys.REFRESH_TOKEN, INITIAL_REFRESH)
        val refresher =
            RecordingTokenRefresher(
                result = RefreshResult.Success(newAccessToken = ROTATED_ACCESS, newRefreshToken = ROTATED_REFRESH),
            )
        val authenticator = TokenAuthenticator(storage, refresher)

        authenticator.authenticate(route = null, response = unauthorizedResponse())

        assertEquals(INITIAL_REFRESH, refresher.lastRefreshToken)
    }

    @Test
    fun failed_refresh_returns_null_so_okhttp_propagates_the_401_and_app_can_route_to_login() {
        val storage = AuthenticatorTokenStorageFake()
        storage.write(SecureTokenKeys.REFRESH_TOKEN, INITIAL_REFRESH)
        val refresher = RecordingTokenRefresher(result = RefreshResult.Failure)
        val authenticator = TokenAuthenticator(storage, refresher)

        val replay = authenticator.authenticate(route = null, response = unauthorizedResponse())

        assertNull(replay)
    }

    @Test
    fun failed_refresh_clears_both_tokens_so_the_next_request_doesnt_replay_the_dead_credentials() {
        // Refresh token expired or revoked — the only safe state is to
        // forget both tokens. Otherwise the next request would re-attach
        // the stale access token, hit another 401, and infinite-loop the
        // refresh path until the refresh keeps 401-ing.
        val storage = AuthenticatorTokenStorageFake()
        storage.write(SecureTokenKeys.ACCESS_TOKEN, INITIAL_ACCESS)
        storage.write(SecureTokenKeys.REFRESH_TOKEN, INITIAL_REFRESH)
        val refresher = RecordingTokenRefresher(result = RefreshResult.Failure)
        val authenticator = TokenAuthenticator(storage, refresher)

        authenticator.authenticate(route = null, response = unauthorizedResponse())

        assertNull(storage.read(SecureTokenKeys.ACCESS_TOKEN))
        assertNull(storage.read(SecureTokenKeys.REFRESH_TOKEN))
    }

    @Test
    fun second_401_on_an_already_replayed_request_returns_null_so_we_dont_loop_forever() {
        // OkHttp re-invokes the Authenticator if the retried request also
        // 401s. Without a guard we'd refresh-retry-401-refresh forever.
        // priorResponse non-null means "this Response is itself a retry",
        // so on that path we must return null and let the 401 propagate.
        val storage = AuthenticatorTokenStorageFake()
        storage.write(SecureTokenKeys.REFRESH_TOKEN, INITIAL_REFRESH)
        val refresher =
            RecordingTokenRefresher(
                result = RefreshResult.Success(newAccessToken = ROTATED_ACCESS, newRefreshToken = ROTATED_REFRESH),
            )
        val authenticator = TokenAuthenticator(storage, refresher)

        val replay = authenticator.authenticate(route = null, response = chainedUnauthorizedResponse())

        assertNull(replay)
        assertEquals("must not call refresher when this is already a retry", 0, refresher.invocationCount)
    }

    private fun unauthorizedResponse(): Response = Response
        .Builder()
        .request(Request.Builder().url(URL).build())
        .protocol(Protocol.HTTP_1_1)
        .code(UNAUTHORIZED)
        .message("Unauthorized")
        .body("".toResponseBody(TEXT_PLAIN.toMediaType()))
        .build()

    private fun chainedUnauthorizedResponse(): Response {
        val originalRequest = Request.Builder().url(URL).build()
        // OkHttp invariant: priorResponse.body must be null (the body has
        // already been consumed by the upstream layer that produced it).
        // Builder accepts a null body.
        val priorResponse =
            Response
                .Builder()
                .request(originalRequest)
                .protocol(Protocol.HTTP_1_1)
                .code(UNAUTHORIZED)
                .message("Unauthorized (initial)")
                .build()
        return Response
            .Builder()
            .request(originalRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(UNAUTHORIZED)
            .message("Unauthorized (retry)")
            .body("".toResponseBody(TEXT_PLAIN.toMediaType()))
            .priorResponse(priorResponse)
            .build()
    }
}

/** Test double: records the refresh token it was passed + count, returns the configured result. */
private class RecordingTokenRefresher(
    private val result: RefreshResult,
) : TokenRefresher {
    var invocationCount: Int = 0
    var lastRefreshToken: String? = null

    override fun refresh(refreshToken: String): RefreshResult {
        invocationCount++
        lastRefreshToken = refreshToken
        return result
    }
}

/** In-memory [SecureTokenStorage] reused only by this test file (file-private). */
private class AuthenticatorTokenStorageFake : SecureTokenStorage {
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
