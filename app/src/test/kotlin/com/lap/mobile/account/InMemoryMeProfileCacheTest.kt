package com.lap.mobile.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private val SAMPLE_PROFILE: MeProfile = MeProfile(
    id = "u_01JX9Z8M4KTRP5D6Y3V2N7H8KQ",
    email = "driver@example.com",
    displayName = "Some Driver",
)

private val OTHER_PROFILE: MeProfile = MeProfile(
    id = "u_01JX9Z8M4KOTHERID9999999999",
    email = "other@example.com",
    displayName = null,
)

/**
 * Locks T189 contract for [InMemoryMeProfileCache] — the v1
 * single-process cache that holds the most-recently-fetched
 * [MeProfile].
 *
 * v1 is in-memory because:
 *   - Process death after login but before any UI consumption is
 *     rare and gracefully handled — next foreground will re-fire
 *     [SyncMeProfileUseCase] via T166's auth-restore kick path.
 *   - Persisting profile (email, displayName) to SharedPreferences
 *     means that data lives on disk encoded; even if not encrypted,
 *     it's PII. Keeping it process-local until there's a clear UX
 *     need ("show last user's email pre-login") avoids speculative
 *     PII surface.
 *   - The contract here doesn't change when persistence lands —
 *     [MeProfileCache] is the seam, swap the impl.
 */
class InMemoryMeProfileCacheTest {
    @Test
    fun cache_starts_empty_so_first_read_before_sync_is_null_and_callers_know_to_fetch() {
        val cache = InMemoryMeProfileCache()

        assertNull(cache.read())
    }

    @Test
    fun write_then_read_round_trips_so_sync_use_case_can_persist_for_indicator_to_consume() {
        val cache = InMemoryMeProfileCache()

        cache.write(SAMPLE_PROFILE)

        assertEquals(SAMPLE_PROFILE, cache.read())
    }

    @Test
    fun second_write_overwrites_first_so_account_switch_or_re_sync_replaces_stale_profile() {
        // Forward-compat: even though v1 is single-account, the cache
        // semantic is "current profile". A future "switch account"
        // path will write the new profile and the old one MUST be
        // gone — read should return only the latest write.
        val cache = InMemoryMeProfileCache()
        cache.write(SAMPLE_PROFILE)

        cache.write(OTHER_PROFILE)

        assertEquals(OTHER_PROFILE, cache.read())
    }

    @Test
    fun clear_resets_to_empty_so_logout_or_session_expiry_can_drop_stale_identity() {
        // T175 logout / T186 session expiry should be able to call
        // clear() so subsequent reads don't surface a profile that
        // no longer corresponds to a valid session. (Wiring those
        // call sites is a separate task; this test locks the cache
        // contract so they can.)
        val cache = InMemoryMeProfileCache()
        cache.write(SAMPLE_PROFILE)

        cache.clear()

        assertNull(cache.read())
    }
}
