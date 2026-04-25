package com.lap.mobile.account

import com.lap.mobile.upload.AuthState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks T189's "on login, fetch /me" trigger predicate.
 *
 * Mirror-image shape of T186's [com.lap.mobile.auth.SessionExpiredDetectionPolicy]:
 *   - T186 fires on Authenticated→Anonymous (passive expiry surface).
 *   - T189 fires on Anonymous→Authenticated (login → fetch profile).
 *
 * Only the *transition into* Authenticated triggers — staying
 * Authenticated doesn't re-fetch (we already have the profile),
 * cold-launch with already-Authenticated doesn't fetch
 * (previous=null), and Anonymous-to-Anonymous obviously doesn't.
 *
 * Why not also fire on cold-launch+Authenticated: the InMemory cache
 * (T189 v1) is empty at process start, so technically a cold-launch
 * with cached refresh token would benefit from a re-fetch. But
 * [com.lap.mobile.upload.AuthRestoreKickPolicy] (T166) already fires
 * on that exact transition (null→Authenticated) for upload draining;
 * the profile sync can ride that same cold-launch trigger via a
 * separate orchestrator without polluting THIS policy with two
 * different "I want to fetch" semantics. Keep this predicate one-rule.
 */
class ProfileSyncTriggerPolicyTest {
    @Test
    fun anonymous_to_authenticated_triggers_sync_so_login_fetches_me_profile_per_T189_acceptance() {
        // Headline T189 invariant: just-logged-in user gets their
        // profile fetched and cached without any explicit call from
        // the UI layer.
        assertTrue(
            ProfileSyncTriggerPolicy.shouldTriggerSync(
                previous = AuthState.Anonymous,
                current = AuthState.Authenticated,
            ),
        )
    }

    @Test
    fun authenticated_to_authenticated_doesnt_trigger_so_token_rotation_doesnt_re_fetch_profile() {
        // T174's silent refresh produces Authenticated→Authenticated
        // (technically the AuthState observer probably wouldn't even
        // re-emit, but defensively the predicate must say no — a
        // refresh isn't a login).
        assertFalse(
            ProfileSyncTriggerPolicy.shouldTriggerSync(
                previous = AuthState.Authenticated,
                current = AuthState.Authenticated,
            ),
        )
    }

    @Test
    fun authenticated_to_anonymous_doesnt_trigger_so_logout_or_session_expiry_doesnt_fetch_for_dead_session() {
        // Symmetric guard — "T186's" transition direction must not
        // trigger T189. Fetching /me with no valid token would just
        // 401.
        assertFalse(
            ProfileSyncTriggerPolicy.shouldTriggerSync(
                previous = AuthState.Authenticated,
                current = AuthState.Anonymous,
            ),
        )
    }

    @Test
    fun anonymous_to_anonymous_doesnt_trigger_so_unauthed_observer_emissions_dont_fire_a_useless_fetch() {
        assertFalse(
            ProfileSyncTriggerPolicy.shouldTriggerSync(
                previous = AuthState.Anonymous,
                current = AuthState.Anonymous,
            ),
        )
    }

    @Test
    fun cold_launch_with_authenticated_doesnt_trigger_because_T166_handles_that_transition() {
        // Cold launch with cached refresh token → first observation
        // is Authenticated, previous=null. T166 AuthRestoreKickPolicy
        // already fires on this exact transition for upload draining;
        // a separate orchestrator can ride that same trigger for
        // profile sync. This predicate stays one-rule and doesn't
        // duplicate T166's job.
        assertFalse(
            ProfileSyncTriggerPolicy.shouldTriggerSync(
                previous = null,
                current = AuthState.Authenticated,
            ),
        )
    }

    @Test
    fun cold_launch_with_anonymous_doesnt_trigger_so_first_install_doesnt_fetch_for_no_one() {
        assertFalse(
            ProfileSyncTriggerPolicy.shouldTriggerSync(
                previous = null,
                current = AuthState.Anonymous,
            ),
        )
    }
}
