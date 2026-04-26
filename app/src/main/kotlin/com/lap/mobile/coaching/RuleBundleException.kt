package com.lap.mobile.coaching

/**
 * Sealed exception hierarchy for rules-engine failures (T211 —
 * port of the desktop Python flat exception types
 * `RuleBundleParseError` / `RuleBundleFetchError` /
 * `RuleBundleCacheError` / `RuleConditionError`).
 *
 * Sealed (not flat) so callers can exhaustively `when`-pattern
 * over recovery strategies — Kotlin's compiler enforces that
 * every variant has a handler. The desktop's flat exception
 * types lose this: Python's `try/except (FetchError, ParseError)`
 * doesn't tell you which it caught without `isinstance`.
 *
 * Recovery semantics by variant (callers should pattern-match):
 *   - [Parse]:     server returned malformed bundle. Cache is
 *                  authoritative-but-might-be-corrupt. Surface
 *                  to error logging; consider falling back to
 *                  cache if fresh.
 *   - [Fetch]:     network / HTTP failure. Fall back to cache
 *                  (T212's resolution does this automatically).
 *   - [Cache]:     signature mismatch on the cache. **Tampering
 *                  signal** — escalate to error logging, do NOT
 *                  silently degrade. Distinct from a routine
 *                  cache miss (which is `null` from the cache,
 *                  not an exception).
 *   - [Condition]: rule's `condition` dict is malformed or uses
 *                  an unsupported operator. Almost always a
 *                  server bug or out-of-date client. Fail
 *                  loudly — a silent no-fire would become an
 *                  invisible coaching gap.
 */
sealed class RuleBundleException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /**
     * Bundle payload couldn't be parsed into typed [RuleBundle] /
     * [Rule] — invalid JSON, missing required fields, wrong types.
     */
    class Parse(message: String, cause: Throwable? = null) :
        RuleBundleException(message, cause)

    /**
     * HTTP fetch failed — network error, non-2xx status, or
     * malformed-but-200-status response.
     */
    class Fetch(message: String, cause: Throwable? = null) :
        RuleBundleException(message, cause)

    /**
     * Cache load failed *signature verification* on a payload
     * that was otherwise fresh & parseable — a tampering signal
     * worth escalating, not just "fall back to network".
     *
     * Missing / corrupt / TTL-expired loads collapse to `null`
     * from the cache instead so callers cold-start by re-fetching
     * — those aren't this exception.
     */
    class Cache(message: String, cause: Throwable? = null) :
        RuleBundleException(message, cause)

    /**
     * A rule's `condition` dict is malformed or references an
     * unsupported operator — almost always a server-side bug or
     * an out-of-date client. Fail loudly so the bug surfaces in
     * error logs / Sentry rather than becoming a silent coaching
     * gap.
     */
    class Condition(message: String, cause: Throwable? = null) :
        RuleBundleException(message, cause)
}
