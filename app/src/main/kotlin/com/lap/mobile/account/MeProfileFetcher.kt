package com.lap.mobile.account

/**
 * Fetches the current user's profile from the server (T189).
 *
 * Modelled as a `fun interface` — same shape as T179's
 * [com.lap.mobile.auth.PairingRedeemer] and T174's
 * [com.lap.mobile.auth.TokenRefresher] — so the production HTTP
 * impl (Retrofit `GET /me`) can be swapped for an in-test fake
 * without dragging Hilt or MockWebServer into JVM unit tests.
 *
 * The contract is total: any error mode (4xx, 5xx, IOException,
 * malformed body, missing fields) collapses into one of the explicit
 * [MeFetchResult.Unauthorized] / [MeFetchResult.NetworkFailure]
 * branches — no exceptions tunnel out, so callers don't have to
 * wrap in try/catch.
 */
fun interface MeProfileFetcher {
    suspend fun fetch(): MeFetchResult
}

/**
 * Outcome of a `/me` fetch. Three shapes only — anything the server
 * can return has to map cleanly into one of these so the orchestrator's
 * `when (result)` stays exhaustive.
 *
 * The split between [Unauthorized] and [NetworkFailure] matters for
 * downstream UX: Unauthorized signals "your refresh token is dead,
 * route to T186's session-expired modal"; NetworkFailure signals
 * "transient, silently retry / back off" without surfacing UX.
 */
sealed interface MeFetchResult {
    /** Server returned 200 with a valid [MeProfile]. */
    data class Success(val profile: MeProfile) : MeFetchResult

    /** Server returned 401/403 — refresh token dead or revoked. */
    data object Unauthorized : MeFetchResult

    /** No connectivity, server timeout, 5xx, malformed body — anything that's "try again later". */
    data object NetworkFailure : MeFetchResult
}
