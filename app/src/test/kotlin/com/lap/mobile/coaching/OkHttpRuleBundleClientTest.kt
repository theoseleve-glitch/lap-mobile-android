package com.lap.mobile.coaching

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Locks the wire contract for [OkHttpRuleBundleClient] (T212) — the
 * production OkHttp-backed implementation of T211's [RuleBundleClient]
 * interface.
 *
 * Why MockWebServer instead of a hand-faked [okhttp3.Call.Factory]:
 * `Call` is an interface but has 8+ methods (request, execute,
 * enqueue, cancel, clone, timeout, …). Faking each one for every
 * test is brittle — MockWebServer is an in-process HTTP server
 * that runs in milliseconds, exercises the *entire* OkHttp stack
 * (interceptors, response parsing, body buffering), and lets
 * `takeRequest()` inspect what was actually sent. Canonical
 * OkHttp test pattern.
 *
 * Coverage: every error-mapping path the production impl
 * collapses into [RuleBundleException.Fetch] + the happy path
 * preserves both parsed bundle and verbatim raw payload (T213's
 * cache needs the raw bytes for byte-for-byte signature
 * verification).
 *
 * Auth header verification matters because: the bundle endpoint
 * is per-user (a free-tier user gets a different rule subset than
 * a paid user, per ADR-013's eventual gating). A request without
 * the Bearer token would either 401 (best case) or fetch a
 * shared default bundle (worst case). Locking the header presence
 * here catches a future refactor that drops it.
 */
class OkHttpRuleBundleClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpRuleBundleClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        // Tight timeouts so test-server hangs surface as test failures
        // immediately rather than burning the JVM idle timer.
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(SHORT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(SHORT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        client = OkHttpRuleBundleClient(
            callFactory = httpClient,
            baseUrl = server.url("").toString().trimEnd('/'),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun fetch_returns_bundle_on_two_hundred_so_happy_path_decodes_server_payload() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(HTTP_OK)
                .setBody(CANONICAL_BUNDLE_PAYLOAD),
        )

        val fetched = client.fetch(authToken = "test-token")

        assertEquals("bundle-2026-04-26", fetched.bundle.bundleId)
        assertEquals(1, fetched.bundle.rules.size)
        assertEquals(
            "br.late_braking.apex_speed_loss",
            fetched.bundle.rules.single().id,
        )
    }

    @Test
    fun fetch_preserves_raw_payload_byte_for_byte_so_T213_cache_can_signature_verify_unchanged_bytes() {
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(HTTP_OK)
                    .setBody(CANONICAL_BUNDLE_PAYLOAD),
            )

            val fetched = client.fetch(authToken = "test-token")

            // Raw payload must be the verbatim server bytes — re-
            // encoding via lapJson would change whitespace / key
            // ordering and break HMAC byte-for-byte verification.
            assertEquals(CANONICAL_BUNDLE_PAYLOAD, fetched.rawPayload)
        }
    }

    @Test
    fun fetch_sends_bearer_authorization_header_so_backend_can_authenticate_user() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(HTTP_OK)
                .setBody(CANONICAL_BUNDLE_PAYLOAD),
        )

        client.fetch(authToken = "abc-secret-token")

        val recorded = server.takeRequest()
        assertEquals("Bearer abc-secret-token", recorded.getHeader("Authorization"))
    }

    @Test
    fun fetch_targets_v1_coaching_rules_bundle_path_so_backend_routes_correctly() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(HTTP_OK)
                .setBody(CANONICAL_BUNDLE_PAYLOAD),
        )

        client.fetch(authToken = "test-token")

        val recorded = server.takeRequest()
        assertEquals("/api/v1/coaching/rules-bundle", recorded.path)
        assertEquals("GET", recorded.method)
    }

    @Test
    fun fetch_throws_fetch_exception_on_unauthorized_so_caller_falls_back_to_cache() {
        server.enqueue(MockResponse().setResponseCode(HTTP_UNAUTHORIZED))

        val exception = assertThrows(RuleBundleException.Fetch::class.java) {
            runTest { client.fetch(authToken = "stale-token") }
        }
        assertTrue(
            "Expected message to mention HTTP code, got: ${exception.message}",
            exception.message!!.contains(HTTP_UNAUTHORIZED.toString()),
        )
    }

    @Test
    fun fetch_throws_fetch_exception_on_server_error_so_transient_500s_dont_corrupt_state() {
        server.enqueue(MockResponse().setResponseCode(HTTP_SERVER_ERROR))

        val exception = assertThrows(RuleBundleException.Fetch::class.java) {
            runTest { client.fetch(authToken = "test-token") }
        }
        assertTrue(
            exception.message!!.contains(HTTP_SERVER_ERROR.toString()),
        )
    }

    @Test
    fun fetch_throws_fetch_exception_on_malformed_json_so_garbage_response_doesnt_silently_succeed() {
        server.enqueue(
            MockResponse()
                .setResponseCode(HTTP_OK)
                .setBody("{not json"),
        )

        val exception = assertThrows(RuleBundleException.Fetch::class.java) {
            runTest { client.fetch(authToken = "test-token") }
        }
        assertNotNull(exception.cause)
    }

    @Test
    fun fetch_throws_fetch_exception_on_network_failure_so_io_exceptions_dont_tunnel_through() {
        // Shut server down so the connection fails — simulates
        // "no internet" / "server unreachable" condition.
        server.shutdown()

        assertThrows(RuleBundleException.Fetch::class.java) {
            runTest { client.fetch(authToken = "test-token") }
        }
    }

    private companion object {
        const val SHORT_TIMEOUT_SECONDS: Long = 2L
        const val HTTP_OK: Int = 200
        const val HTTP_UNAUTHORIZED: Int = 401
        const val HTTP_SERVER_ERROR: Int = 500

        // Mirror of T211's RuleBundleSerializationTest fixture so
        // wire-format alignment between fetch + parse is exercised
        // end-to-end through a real HTTP roundtrip.
        const val CANONICAL_BUNDLE_PAYLOAD: String = """{
  "schema_version": "1.0.0",
  "bundle_id": "bundle-2026-04-26",
  "compiled_at": "2026-04-26T08:00:00Z",
  "rules": [
    {
      "id": "br.late_braking.apex_speed_loss",
      "category": "technique",
      "condition": {"type": "feature_threshold", "feature": "apex_speed", "comparison": "lt", "value": 80.0},
      "insight": {"category": "braking", "severity": "medium"},
      "min_corroborating_laps": 3
    }
  ]
}"""
    }
}
