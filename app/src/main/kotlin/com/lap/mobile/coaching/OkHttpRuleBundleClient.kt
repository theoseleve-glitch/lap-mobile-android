package com.lap.mobile.coaching

import com.lap.mobile.core.lapJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.Call
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject

private const val RULES_BUNDLE_PATH: String = "/api/v1/coaching/rules-bundle"
private const val AUTHORIZATION_HEADER: String = "Authorization"
private const val BEARER_PREFIX: String = "Bearer "

/**
 * Production [RuleBundleClient] backed by OkHttp (T212).
 *
 * Hits the `/api/v1/coaching/rules-bundle` endpoint (matches the
 * desktop spec's path so server routing stays unified across
 * clients), authenticates via per-call Bearer token (so the same
 * client instance survives token rotation per T173/T174), and
 * preserves the verbatim raw payload so T213's cache can byte-
 * for-byte signature-verify.
 *
 * Why inject [Call.Factory] not [okhttp3.OkHttpClient] directly:
 * `OkHttpClient` *implements* `Call.Factory` (single-method
 * interface: `newCall(request) -> Call`). Depending on the smaller
 * interface keeps this impl loosely coupled — tests can swap
 * fakes, future tasks can wrap the client with auth interceptors
 * (T174's Authenticator) without changing this signature. Standard
 * "depend on interfaces" pattern OkHttp's docs recommend.
 *
 * Why [Dispatchers.IO]: OkHttp's `execute()` is synchronous
 * blocking I/O. The interface is `suspend`, so wrap the call in
 * `withContext(Dispatchers.IO)` to honor coroutine cancellation
 * and free the calling dispatcher (typically Dispatchers.Main on
 * Android UI). Cleaner than `enqueue + suspendCancellableCoroutine`
 * for a single sync call.
 *
 * Error mapping — every failure mode collapses to
 * [RuleBundleException.Fetch]:
 *   - Non-2xx HTTP status → "server returned HTTP ${code}"
 *   - Empty response body → "server returned empty body"
 *   - Malformed JSON 200 → "server returned malformed bundle"
 *   - IOException (network / timeout / connection refused) →
 *     "network error: ${message}"
 *
 * No distinction between Fetch and Parse for the fetch path: the
 * cache-fallback decision is the same regardless of root cause
 * ("server didn't give me a usable bundle"). Wrapping the
 * SerializationException as Fetch (not Parse) keeps the contract
 * narrow — Parse is reserved for cache-load malformations
 * (T213).
 */
class OkHttpRuleBundleClient
@Inject
constructor(
    private val callFactory: Call.Factory,
    private val baseUrl: String,
) : RuleBundleClient {
    override suspend fun fetch(authToken: String): FetchedBundle = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl$RULES_BUNDLE_PATH")
            .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$authToken")
            .get()
            .build()

        val response = try {
            callFactory.newCall(request).execute()
        } catch (e: IOException) {
            throw RuleBundleException.Fetch("network error: ${e.message}", cause = e)
        }

        response.use { resp ->
            if (!resp.isSuccessful) {
                throw RuleBundleException.Fetch("server returned HTTP ${resp.code}")
            }
            val rawPayload = resp.body?.string()
                ?: throw RuleBundleException.Fetch("server returned empty body")
            val bundle = try {
                lapJson.decodeFromString<RuleBundle>(rawPayload)
            } catch (e: SerializationException) {
                throw RuleBundleException.Fetch(
                    "server returned malformed bundle: ${e.message}",
                    cause = e,
                )
            }
            FetchedBundle(bundle = bundle, rawPayload = rawPayload)
        }
    }
}
