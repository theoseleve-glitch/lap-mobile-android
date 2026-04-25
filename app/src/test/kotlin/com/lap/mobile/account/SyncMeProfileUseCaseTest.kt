package com.lap.mobile.account

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

private val FETCHED_PROFILE: MeProfile = MeProfile(
    id = "u_01JX9Z8M4KTRP5D6Y3V2N7H8KQ",
    email = "driver@example.com",
    displayName = "Some Driver",
)

/**
 * Locks T189 contract for [SyncMeProfileUseCase] — the orchestrator
 * that turns a fetched [MeProfile] into a populated [MeProfileCache]
 * entry on success, leaves the cache untouched on failure.
 *
 * The acceptance "fetch /me and populate local cache ⇒ tested"
 * splits cleanly into two use-case behaviors:
 *   - On [MeFetchResult.Success]: write to cache, return the result.
 *   - On any failure: pass-through the result, do NOT mutate cache.
 *
 * The "do NOT mutate on failure" branch matters because cache holds
 * the previously-cached profile (if any). A failed re-sync should
 * leave the indicator showing "driver@example.com" rather than
 * blanking out — last-known-good is more useful than nothing.
 *
 * Production [MeProfileFetcher] HTTP impl is [PENDING:
 * device-required] — backend `/me` endpoint spec required. The
 * orchestration locked here works with whatever impl lands.
 */
class SyncMeProfileUseCaseTest {
    @Test
    fun successful_fetch_writes_profile_into_cache_so_indicator_can_consume_post_login() {
        runTest {
            val cache = InMemoryMeProfileCache()
            val useCase = SyncMeProfileUseCase(
                fetcher = { MeFetchResult.Success(FETCHED_PROFILE) },
                cache = cache,
            )

            useCase.sync()

            assertEquals(FETCHED_PROFILE, cache.read())
        }
    }

    @Test
    fun successful_fetch_returns_success_result_so_caller_can_react_to_outcome() {
        runTest {
            val cache = InMemoryMeProfileCache()
            val useCase = SyncMeProfileUseCase(
                fetcher = { MeFetchResult.Success(FETCHED_PROFILE) },
                cache = cache,
            )

            val result = useCase.sync()

            assertEquals(MeFetchResult.Success(FETCHED_PROFILE), result)
        }
    }

    @Test
    fun unauthorized_fetch_doesnt_write_cache_so_an_invalid_token_path_doesnt_clobber_last_known_profile() {
        runTest {
            val cache = InMemoryMeProfileCache()
            val useCase = SyncMeProfileUseCase(
                fetcher = { MeFetchResult.Unauthorized },
                cache = cache,
            )

            useCase.sync()

            assertNull(cache.read())
        }
    }

    @Test
    fun unauthorized_fetch_propagates_result_so_orchestrator_can_route_to_re_pair_or_session_expired() {
        runTest {
            val cache = InMemoryMeProfileCache()
            val useCase = SyncMeProfileUseCase(
                fetcher = { MeFetchResult.Unauthorized },
                cache = cache,
            )

            val result = useCase.sync()

            assertSame(MeFetchResult.Unauthorized, result)
        }
    }

    @Test
    fun network_failure_doesnt_write_cache_so_offline_re_sync_doesnt_blank_out_last_known_profile() {
        // Last-known-good is more useful than nothing: if the cache
        // already held a profile from a previous successful sync, a
        // network blip during re-sync MUST leave that profile in
        // place. The indicator continues showing
        // "driver@example.com" rather than reverting to "Paired".
        runTest {
            val cache = InMemoryMeProfileCache()
            cache.write(FETCHED_PROFILE)
            val useCase = SyncMeProfileUseCase(
                fetcher = { MeFetchResult.NetworkFailure },
                cache = cache,
            )

            useCase.sync()

            assertEquals(FETCHED_PROFILE, cache.read())
        }
    }

    @Test
    fun network_failure_propagates_result_so_orchestrator_can_silently_retry_or_back_off() {
        runTest {
            val cache = InMemoryMeProfileCache()
            val useCase = SyncMeProfileUseCase(
                fetcher = { MeFetchResult.NetworkFailure },
                cache = cache,
            )

            val result = useCase.sync()

            assertSame(MeFetchResult.NetworkFailure, result)
        }
    }
}
