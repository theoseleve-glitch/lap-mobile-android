package com.lap.mobile.upload

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TOKEN_A: String = "first-token"
private const val TOKEN_B: String = "refreshed-token"
private const val URL: String = "https://ingest.example/api/v1/sessions/ingest"
private const val TEXT_PLAIN: String = "text/plain"
private const val UNAUTHORIZED: Int = 401
private const val OK: Int = 200
private const val SERVER_ERROR: Int = 500

class AuthInterceptorTest {
    @Test
    fun adds_authorization_header_with_current_token_on_first_call() {
        val captured = mutableListOf<Request>()
        val interceptor = AuthInterceptor(StubTokenProvider(current = TOKEN_A))

        interceptor.handle(buildRequest()) { req ->
            captured.add(req)
            okResponse(req)
        }

        assertEquals("Bearer $TOKEN_A", captured.first().header("Authorization"))
    }

    @Test
    fun does_not_retry_on_2xx_so_a_single_call_is_one_request() {
        val captured = mutableListOf<Request>()
        val interceptor = AuthInterceptor(StubTokenProvider(current = TOKEN_A))

        interceptor.handle(buildRequest()) { req ->
            captured.add(req)
            okResponse(req)
        }

        assertEquals("happy-path must NOT trigger a second request", 1, captured.size)
    }

    @Test
    fun does_not_retry_on_5xx_because_token_was_already_valid() {
        val captured = mutableListOf<Request>()
        val interceptor = AuthInterceptor(StubTokenProvider(current = TOKEN_A))

        interceptor.handle(buildRequest()) { req ->
            captured.add(req)
            errorResponse(req, code = SERVER_ERROR)
        }

        assertEquals("server error must NOT trigger a token refresh", 1, captured.size)
    }

    @Test
    fun on_401_refreshes_token_and_retries_once_with_new_bearer() {
        val captured = mutableListOf<Request>()
        val provider = StubTokenProvider(current = TOKEN_A, refreshed = TOKEN_B)
        val interceptor = AuthInterceptor(provider)

        val response = interceptor.handle(buildRequest()) { req ->
            captured.add(req)
            if (captured.size == 1) errorResponse(req, code = UNAUTHORIZED) else okResponse(req)
        }

        assertEquals(2, captured.size)
        assertEquals("Bearer $TOKEN_A", captured[0].header("Authorization"))
        assertEquals("Bearer $TOKEN_B", captured[1].header("Authorization"))
        assertEquals(OK, response.code)
        assertTrue("token refresh must have been attempted", provider.refreshCount == 1)
    }

    @Test
    fun on_401_with_failed_refresh_returns_the_original_401_unchanged() {
        // Refresh failure (e.g., refresh token expired) — surface 401 to
        // the caller rather than retrying with a stale token.
        val captured = mutableListOf<Request>()
        val provider = StubTokenProvider(current = TOKEN_A, refreshed = null)
        val interceptor = AuthInterceptor(provider)

        val response = interceptor.handle(buildRequest()) { req ->
            captured.add(req)
            errorResponse(req, code = UNAUTHORIZED)
        }

        assertEquals(1, captured.size)
        assertEquals(UNAUTHORIZED, response.code)
    }

    @Test
    fun no_token_available_means_request_goes_out_without_authorization_header() {
        // Anonymous calls (e.g., login itself) — interceptor still works
        // but doesn't fabricate a header.
        val captured = mutableListOf<Request>()
        val interceptor = AuthInterceptor(StubTokenProvider(current = null))

        interceptor.handle(buildRequest()) { req ->
            captured.add(req)
            okResponse(req)
        }

        assertNull(captured.first().header("Authorization"))
    }

    private fun buildRequest(): Request = Request.Builder().url(URL).build()

    private fun okResponse(req: Request): Response = Response.Builder()
        .request(req)
        .protocol(Protocol.HTTP_1_1)
        .code(OK)
        .message("OK")
        .body("".toResponseBody(TEXT_PLAIN.toMediaType()))
        .build()

    private fun errorResponse(req: Request, code: Int): Response = Response.Builder()
        .request(req)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("error $code")
        .body("error body".toResponseBody(TEXT_PLAIN.toMediaType()))
        .build()
}

private class StubTokenProvider(
    private val current: String?,
    private val refreshed: String? = null,
) : TokenProvider {
    var refreshCount: Int = 0

    override fun current(): String? = current

    override fun refresh(): String? {
        refreshCount++
        return refreshed
    }
}
