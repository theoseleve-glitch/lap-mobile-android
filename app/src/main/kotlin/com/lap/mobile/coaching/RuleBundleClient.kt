package com.lap.mobile.coaching

/**
 * HTTP client contract for the compiled rules-bundle endpoint
 * (T211 — port of the desktop Python
 * `datalogger.coaching.rules_engine.RuleBundleHttpClient`).
 *
 * T212 implements this against the `/api/v1/coaching/rules-bundle`
 * endpoint using Ktor / OkHttp (TBD by T212). T214 / T215 depend
 * on the *interface*, not the impl, so test code can substitute
 * an in-memory fake without touching the production HTTP stack.
 *
 * `suspend` from day one — Android networking must be off-main-
 * thread, and Ktor / OkHttp's coroutine APIs are suspending. T212
 * inherits that without retrofitting.
 *
 * Auth is passed at call-time (per-call Bearer token) so the same
 * client instance survives token rotation — same shape as the
 * desktop spec.
 */
interface RuleBundleClient {
    /**
     * Fetch the latest compiled rules bundle from the server.
     *
     * @param authToken Bearer token for the request. Caller is
     *   responsible for token freshness — T173's
     *   SecureTokenStorage + T174's Authenticator handle rotation
     *   upstream of this call.
     * @return [FetchedBundle] containing the parsed bundle plus
     *   the verbatim raw payload (T213's cache persists the raw
     *   payload so signature verification can happen byte-for-byte).
     * @throws RuleBundleException.Fetch on any network failure,
     *   non-2xx HTTP status, or malformed response. Distinct from
     *   [RuleBundleException.Parse] so callers can decide between
     *   "fall back to cache" (fetch error) vs "cache is corrupt"
     *   (parse error).
     */
    suspend fun fetch(authToken: String): FetchedBundle
}

/**
 * One server-fetched bundle paired with its raw payload.
 *
 * The raw payload is exposed verbatim so T213's cache can persist
 * it byte-for-byte, enabling signature verification on the cached
 * bytes (not on a re-encoded round-trip, which could differ in
 * whitespace / key ordering and break HMAC validation).
 */
data class FetchedBundle(
    val bundle: RuleBundle,
    val rawPayload: String,
)
