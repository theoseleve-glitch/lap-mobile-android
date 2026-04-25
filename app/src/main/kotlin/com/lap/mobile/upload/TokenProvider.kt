package com.lap.mobile.upload

/**
 * Source of bearer tokens for outgoing API calls (T149).
 *
 * [current] returns the cached token if any, or null for
 * anonymous calls (e.g., the login request itself). [refresh]
 * is invoked by [AuthInterceptor] after a 401 to obtain a new
 * token; returning null means the refresh attempt failed and
 * the caller should surface the 401 unchanged.
 *
 * The Clerk-backed implementation lands with the auth wiring
 * in Block I (T171+); upload code only depends on this seam so
 * it stays JVM-testable today.
 */
interface TokenProvider {
    fun current(): String?

    fun refresh(): String?
}
