package com.lap.mobile.upload

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRestoreKickPolicyTest {
    @Test
    fun first_observation_authenticated_kicks_so_app_launch_with_a_cached_token_drains_pending_uploads() {
        // App restored after restart while still signed in. Any queued
        // rows that 401'd just before exit (T149) will retry once the
        // worker picks them up — kicking here doesn't wait for the next
        // periodic WorkManager poll.
        val shouldKick = AuthRestoreKickPolicy.shouldKickQueue(
            previous = null,
            current = AuthState.Authenticated,
        )

        assertTrue(shouldKick)
    }

    @Test
    fun first_observation_anonymous_does_not_kick_because_user_still_has_to_log_in_for_uploads_to_succeed() {
        val shouldKick = AuthRestoreKickPolicy.shouldKickQueue(
            previous = null,
            current = AuthState.Anonymous,
        )

        assertFalse(shouldKick)
    }

    @Test
    fun transitioning_from_anonymous_to_authenticated_kicks_so_uploads_retry_the_moment_the_user_logs_in() {
        // Headline behavior: while logged out, AuthInterceptor (T149)
        // forwards anonymous requests; uploads 401 and stay queued for
        // backoff retry. The moment a fresh token lands, kick so those
        // rows retry now rather than waiting for the BackoffSchedule
        // (T154) deadline to elapse.
        val shouldKick = AuthRestoreKickPolicy.shouldKickQueue(
            previous = AuthState.Anonymous,
            current = AuthState.Authenticated,
        )

        assertTrue(shouldKick)
    }

    @Test
    fun transitioning_from_authenticated_to_anonymous_does_not_kick_because_uploads_would_just_fail_401() {
        val shouldKick = AuthRestoreKickPolicy.shouldKickQueue(
            previous = AuthState.Authenticated,
            current = AuthState.Anonymous,
        )

        assertFalse(shouldKick)
    }

    @Test
    fun repeated_authenticated_observations_do_not_kick_so_token_refresh_in_same_session_doesnt_thrash_worker() {
        // TokenProvider.refresh (T149) issues a new bearer mid-session
        // without changing the AuthState — we're still Authenticated.
        // A redundant kick would do nothing useful (T159 KEEP dedupes
        // anyway, but staying quiet at the policy layer is cleaner).
        val shouldKick = AuthRestoreKickPolicy.shouldKickQueue(
            previous = AuthState.Authenticated,
            current = AuthState.Authenticated,
        )

        assertFalse(shouldKick)
    }

    @Test
    fun repeated_anonymous_observations_do_not_kick_because_user_remains_logged_out() {
        val shouldKick = AuthRestoreKickPolicy.shouldKickQueue(
            previous = AuthState.Anonymous,
            current = AuthState.Anonymous,
        )

        assertFalse(shouldKick)
    }
}
