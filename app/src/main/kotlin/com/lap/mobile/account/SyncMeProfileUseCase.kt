package com.lap.mobile.account

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the /me sync flow (T189):
 *   - On [ProfileSyncTriggerPolicy.shouldTriggerSync] firing,
 *     caller invokes [sync].
 *   - [MeProfileFetcher] returns one of three exhaustive results.
 *   - On [MeFetchResult.Success], the fresh [MeProfile] is written
 *     to [MeProfileCache]; otherwise the cache is left untouched.
 *
 * "Cache untouched on failure" matters for last-known-good UX: if a
 * previous successful sync populated the cache, a failed re-sync
 * (network blip, transient 5xx) MUST leave the existing entry in
 * place. The paired-device indicator continues showing
 * "driver@example.com" rather than reverting to the no-identifier
 * state — last-known-good is more useful than nothing.
 *
 * On [MeFetchResult.Unauthorized] the cache is also left intact —
 * dropping the cached profile is the orchestrator's responsibility
 * (it belongs in T186's HandleSessionExpired flow, which clears the
 * tokens that caused the 401 in the first place). Coupling cache-
 * clear to "every Unauthorized response" would mean every spurious
 * 401 (e.g., from a half-rotated token race) wipes the indicator.
 *
 * Production [MeProfileFetcher] HTTP impl is [PENDING:
 * device-required] — backend `/me` endpoint spec needed. The
 * orchestration locked here works with whatever impl lands.
 */
@Singleton
class SyncMeProfileUseCase
@Inject
constructor(
    private val fetcher: MeProfileFetcher,
    private val cache: MeProfileCache,
) {
    suspend fun sync(): MeFetchResult {
        val result = fetcher.fetch()
        if (result is MeFetchResult.Success) {
            cache.write(result.profile)
        }
        return result
    }
}
