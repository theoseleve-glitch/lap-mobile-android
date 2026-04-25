package com.lap.mobile.auth

import com.lap.mobile.upload.AuthState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EXPIRED_ACCESS: String = "access-that-just-expired"
private const val EXPIRED_REFRESH: String = "refresh-that-server-revoked"

/**
 * Locks T186 contract — surface a sign-in-again modal when the
 * session passively expires, preserve local data so the user can
 * re-authenticate and pick up where they left off.
 *
 * Two pieces:
 *   - [SessionExpiredDetectionPolicy] decides WHEN to surface the
 *     modal: only on Authenticated→Anonymous transitions that
 *     weren't user-initiated (those are T175 logout, which gets a
 *     different UX). Every other transition leaves the modal
 *     hidden.
 *   - [HandleSessionExpired] decides WHAT to do: clear credentials
 *     only. Captured sessions / journal / upload queue stay on disk
 *     so re-authentication restores the working state. T166's
 *     AuthRestoreKickPolicy fires on the next Anonymous→Authenticated
 *     transition, draining the queue automatically.
 *
 * The policy + handler split mirrors T165/T166's connectivity-and-
 * auth kick patterns — predicate kernels stay independent from the
 * side-effect orchestrators.
 */
class SessionExpiredPolicyTest {
    @Test
    fun authenticated_to_anonymous_without_user_intent_surfaces_the_session_expired_modal() {
        // Headline T186 invariant: the user was signed in, the
        // server invalidated the refresh (admin revoke, expired
        // password, suspicious activity rotation), and we want to
        // tell them why they're suddenly being asked to sign in
        // again.
        assertTrue(
            SessionExpiredDetectionPolicy.shouldShowExpiredModal(
                previous = AuthState.Authenticated,
                current = AuthState.Anonymous,
                userInitiatedLogout = false,
            ),
        )
    }

    @Test
    fun authenticated_to_anonymous_with_user_intent_does_NOT_surface_modal_because_thats_T175_logout() {
        // User just tapped Logout. They know why they're signed
        // out — surfacing "your session expired" would be confusing.
        // T175's normal post-logout UX takes over.
        assertFalse(
            SessionExpiredDetectionPolicy.shouldShowExpiredModal(
                previous = AuthState.Authenticated,
                current = AuthState.Anonymous,
                userInitiatedLogout = true,
            ),
        )
    }

    @Test
    fun no_state_change_doesnt_surface_modal_so_a_steady_authed_session_doesnt_blink_a_modal() {
        assertFalse(
            SessionExpiredDetectionPolicy.shouldShowExpiredModal(
                previous = AuthState.Authenticated,
                current = AuthState.Authenticated,
                userInitiatedLogout = false,
            ),
        )
    }

    @Test
    fun cold_launch_anonymous_doesnt_surface_modal_so_first_install_doesnt_show_session_expired() {
        // Previous null = no observation yet. Current Anonymous =
        // user opened the app and isn't signed in. That's the first-
        // run state, not a session expiry — don't show the modal.
        assertFalse(
            SessionExpiredDetectionPolicy.shouldShowExpiredModal(
                previous = null,
                current = AuthState.Anonymous,
                userInitiatedLogout = false,
            ),
        )
    }

    @Test
    fun cold_launch_authenticated_doesnt_surface_modal_so_returning_user_doesnt_see_an_expiry_warning() {
        // Returning user: app boots, restores cached refresh token,
        // first observation is Authenticated. No prior state to
        // compare to → no modal.
        assertFalse(
            SessionExpiredDetectionPolicy.shouldShowExpiredModal(
                previous = null,
                current = AuthState.Authenticated,
                userInitiatedLogout = false,
            ),
        )
    }

    @Test
    fun anonymous_to_authenticated_doesnt_surface_modal_because_thats_a_login_not_an_expiry() {
        assertFalse(
            SessionExpiredDetectionPolicy.shouldShowExpiredModal(
                previous = AuthState.Anonymous,
                current = AuthState.Authenticated,
                userInitiatedLogout = false,
            ),
        )
    }

    @Test
    fun handle_session_expired_clears_both_token_slots_so_next_request_doesnt_replay_dead_credentials() = runTest {
        val storage = SessionExpiredTokenStorageFake()
        storage.write(SecureTokenKeys.ACCESS_TOKEN, EXPIRED_ACCESS)
        storage.write(SecureTokenKeys.REFRESH_TOKEN, EXPIRED_REFRESH)
        val handler = HandleSessionExpired(storage)

        handler.onExpired()

        assertNull(storage.read(SecureTokenKeys.ACCESS_TOKEN))
        assertNull(storage.read(SecureTokenKeys.REFRESH_TOKEN))
    }

    @Test
    fun handle_session_expired_takes_only_token_storage_dep_so_it_structurally_cant_touch_db_or_uploads() {
        // The "preserves local data" half of T186 is enforced
        // structurally: HandleSessionExpired is constructed with
        // ONLY SecureTokenStorage. No UploadWorkCanceller, no
        // LocalDatabaseResetter — the impl literally cannot call
        // them, so a future change accidentally adding "drop the DB
        // on session expiry" would have to also add the dep, which
        // would be visible in code review.
        val constructorParams = HandleSessionExpired::class.constructors.single().parameters
        val paramTypes = constructorParams.map { it.type.toString() }

        assertEquals(1, paramTypes.size)
        assertTrue(
            "Expected single SecureTokenStorage param, got: $paramTypes",
            paramTypes.single().contains("SecureTokenStorage"),
        )
    }
}

private class SessionExpiredTokenStorageFake : SecureTokenStorage {
    private val store = mutableMapOf<String, String>()

    override fun read(key: String): String? = store[key]

    override fun write(key: String, value: String) {
        store[key] = value
    }

    override fun clear(key: String) {
        store.remove(key)
    }

    override fun clearAll() {
        store.clear()
    }
}
