package com.lap.mobile.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val ACCESS: String = "access-token-stored-pre-logout"
private const val REFRESH: String = "refresh-token-stored-pre-logout"
private const val TAG_CANCEL_UPLOADS: String = "cancel-uploads"
private const val TAG_CLEAR_TOKENS: String = "clear-tokens"
private const val TAG_RESET_DB: String = "reset-db"

/**
 * Locks the T175 logout contract — the three side effects must fire in
 * a specific order that prevents in-flight uploads from racing against
 * fresh-blank credential storage.
 *
 * Why ordering matters:
 *   - If we cleared tokens BEFORE cancelling upload workers, an
 *     in-flight worker reading [SecureTokenStorage.read] would see
 *     null, hit 401 against the server, and trigger T174's
 *     [TokenAuthenticator] refresh path — refresh has nothing to
 *     exchange (we just wiped it), so it logs an avoidable
 *     auth-failure noise during a clean logout.
 *   - If we reset the DB BEFORE clearing tokens, a worker that survives
 *     the cancellation race (say, mid-PUT) would still hold a valid
 *     bearer for an account whose local cache is now empty — this is
 *     benign but the audit trail is cleaner if "you have a token" and
 *     "you have local data" are dropped together.
 *
 * Locked order: cancel uploads → clear tokens → reset DB.
 */
class LogoutUseCaseTest {
    @Test
    fun logout_clears_secure_token_storage_so_post_logout_reads_return_null() = runTest {
        val storage = LogoutTokenStorageFake()
        storage.write(SecureTokenKeys.ACCESS_TOKEN, ACCESS)
        storage.write(SecureTokenKeys.REFRESH_TOKEN, REFRESH)
        val useCase =
            LogoutUseCase(
                storage = storage,
                canceller = NoOpUploadWorkCanceller(),
                resetter = NoOpLocalDatabaseResetter(),
            )

        useCase.logout()

        assertNull(storage.read(SecureTokenKeys.ACCESS_TOKEN))
        assertNull(storage.read(SecureTokenKeys.REFRESH_TOKEN))
    }

    @Test
    fun logout_cancels_all_upload_workers_so_no_worker_keeps_running_with_dropped_credentials() = runTest {
        val canceller = RecordingUploadWorkCanceller()
        val useCase =
            LogoutUseCase(
                storage = LogoutTokenStorageFake(),
                canceller = canceller,
                resetter = NoOpLocalDatabaseResetter(),
            )

        useCase.logout()

        assertEquals(1, canceller.invocationCount)
    }

    @Test
    fun logout_resets_local_database_so_a_different_account_signing_in_does_not_inherit_cached_rows() = runTest {
        val resetter = RecordingLocalDatabaseResetter()
        val useCase =
            LogoutUseCase(
                storage = LogoutTokenStorageFake(),
                canceller = NoOpUploadWorkCanceller(),
                resetter = resetter,
            )

        useCase.logout()

        assertEquals(1, resetter.invocationCount)
    }

    @Test
    fun logout_runs_in_strict_order_cancel_uploads_then_clear_tokens_then_reset_db() = runTest {
        val sequence = mutableListOf<String>()
        val storage =
            RecordingTokenStorage { event ->
                if (event == TAG_CLEAR_TOKENS) sequence.add(event)
            }
        val canceller = UploadWorkCanceller { sequence.add(TAG_CANCEL_UPLOADS) }
        val resetter = LocalDatabaseResetter { sequence.add(TAG_RESET_DB) }
        val useCase = LogoutUseCase(storage, canceller, resetter)

        useCase.logout()

        assertEquals(
            listOf(TAG_CANCEL_UPLOADS, TAG_CLEAR_TOKENS, TAG_RESET_DB),
            sequence,
        )
    }

    @Test
    fun logout_called_twice_does_not_throw_so_a_re_tap_during_a_slow_db_reset_is_idempotent() = runTest {
        // Defensive: user double-taps "Logout" or two screens both fire it.
        // Each side effect is already idempotent (clearAll() of empty
        // storage is a noop, cancelAllUploads on no-workers is a noop,
        // resetAll on already-empty DB is a noop). This locks that the
        // use case itself doesn't introduce any not-idempotent state.
        val useCase =
            LogoutUseCase(
                storage = LogoutTokenStorageFake(),
                canceller = NoOpUploadWorkCanceller(),
                resetter = NoOpLocalDatabaseResetter(),
            )

        useCase.logout()
        useCase.logout()

        // Reaches here without throwing — that's the assertion.
    }
}

/** In-memory [SecureTokenStorage] used only by this test file. Suffixed `Fake`
 * to avoid the package-private file-private collision pattern (Kotlin top-level
 * `private class` is JVM package-private). */
private class LogoutTokenStorageFake : SecureTokenStorage {
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

/** Like [LogoutTokenStorageFake] but routes a tag to a recorder on every clearAll(). */
private class RecordingTokenStorage(
    private val onEvent: (String) -> Unit,
) : SecureTokenStorage {
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
        onEvent(TAG_CLEAR_TOKENS)
    }
}

private class RecordingUploadWorkCanceller : UploadWorkCanceller {
    var invocationCount: Int = 0

    override suspend fun cancelAllUploads() {
        invocationCount++
    }
}

private class NoOpUploadWorkCanceller : UploadWorkCanceller {
    override suspend fun cancelAllUploads() = Unit
}

private class RecordingLocalDatabaseResetter : LocalDatabaseResetter {
    var invocationCount: Int = 0

    override suspend fun resetAll() {
        invocationCount++
    }
}

private class NoOpLocalDatabaseResetter : LocalDatabaseResetter {
    override suspend fun resetAll() = Unit
}
