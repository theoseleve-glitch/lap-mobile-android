package com.lap.mobile.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val TAG_REVOKE: String = "revoke-server"
private const val TAG_LOGOUT: String = "local-logout"
private const val ACCESS_PRE_UNPAIR: String = "access-while-paired"
private const val REFRESH_PRE_UNPAIR: String = "refresh-while-paired"

/**
 * Locks T182 unpair contract: POST /auth/devices/:id/revoke + local
 * cleanup. The "tested" acceptance hinges on three invariants:
 *
 *  1. Revoke runs BEFORE logout — logout wipes credentials, and the
 *     revoke call needs a valid bearer to authenticate to the server.
 *     Reversed order means an unauthenticated revoke that the server
 *     can't honor.
 *  2. Local logout runs REGARDLESS of revoke result. The user tapped
 *     "unpair this device"; if the server is unreachable, we still
 *     respect the intent locally. The next /auth/refresh would 401
 *     anyway, so the user effectively IS unpaired — failing to log
 *     out locally would trap them in a state they tried to leave.
 *  3. The outcome surfaces the divergence (revoked-cleanly vs
 *     server-unreachable vs already-unauthorized) so the UI can
 *     show a soft warning when the server didn't acknowledge.
 */
class UnpairDeviceUseCaseTest {
    @Test
    fun successful_revoke_runs_local_logout_and_returns_revoked_outcome() = runTest {
        val storage = UnpairTokenStorageFake()
        storage.write(SecureTokenKeys.ACCESS_TOKEN, ACCESS_PRE_UNPAIR)
        storage.write(SecureTokenKeys.REFRESH_TOKEN, REFRESH_PRE_UNPAIR)
        val logout =
            LogoutUseCase(
                storage = storage,
                canceller = UnpairNoOpUploadWorkCanceller(),
                resetter = UnpairNoOpLocalDatabaseResetter(),
            )
        val revoker = RecordingDeviceRevoker(result = RevocationResult.Success)
        val useCase = UnpairDeviceUseCase(revoker, logout)

        val outcome = useCase.unpair()

        assertEquals(UnpairOutcome.UnpairedAndRevoked, outcome)
        assertNull(storage.read(SecureTokenKeys.ACCESS_TOKEN))
        assertNull(storage.read(SecureTokenKeys.REFRESH_TOKEN))
    }

    @Test
    fun network_failure_during_revoke_still_runs_local_logout_returning_server_unreachable_outcome() = runTest {
        // Server unreachable scenario: user is in a tunnel / on
        // airplane mode / WiFi bouncing. Their intent ("unpair this
        // device") shouldn't be defeated by a transient network
        // problem. Logout must still complete locally.
        val storage = UnpairTokenStorageFake()
        storage.write(SecureTokenKeys.ACCESS_TOKEN, ACCESS_PRE_UNPAIR)
        storage.write(SecureTokenKeys.REFRESH_TOKEN, REFRESH_PRE_UNPAIR)
        val logout =
            LogoutUseCase(
                storage = storage,
                canceller = UnpairNoOpUploadWorkCanceller(),
                resetter = UnpairNoOpLocalDatabaseResetter(),
            )
        val revoker = RecordingDeviceRevoker(result = RevocationResult.NetworkFailure)
        val useCase = UnpairDeviceUseCase(revoker, logout)

        val outcome = useCase.unpair()

        assertEquals(UnpairOutcome.UnpairedServerUnreachable, outcome)
        assertNull(storage.read(SecureTokenKeys.ACCESS_TOKEN))
        assertNull(storage.read(SecureTokenKeys.REFRESH_TOKEN))
    }

    @Test
    fun unauthorized_revoke_still_runs_local_logout_returning_already_unauthorized_outcome() = runTest {
        // Server returned 401 — token was already revoked or expired
        // server-side (maybe the user revoked from a different
        // device's settings already). Local cleanup must still
        // happen so the device stops trying to act authenticated.
        val storage = UnpairTokenStorageFake()
        storage.write(SecureTokenKeys.ACCESS_TOKEN, ACCESS_PRE_UNPAIR)
        storage.write(SecureTokenKeys.REFRESH_TOKEN, REFRESH_PRE_UNPAIR)
        val logout =
            LogoutUseCase(
                storage = storage,
                canceller = UnpairNoOpUploadWorkCanceller(),
                resetter = UnpairNoOpLocalDatabaseResetter(),
            )
        val revoker = RecordingDeviceRevoker(result = RevocationResult.Unauthorized)
        val useCase = UnpairDeviceUseCase(revoker, logout)

        val outcome = useCase.unpair()

        assertEquals(UnpairOutcome.UnpairedAlreadyUnauthorized, outcome)
        assertNull(storage.read(SecureTokenKeys.ACCESS_TOKEN))
        assertNull(storage.read(SecureTokenKeys.REFRESH_TOKEN))
    }

    @Test
    fun revoke_runs_before_logout_so_bearer_is_still_valid_when_the_request_fires() = runTest {
        // The revoke call needs the soon-to-be-dead bearer in its
        // Authorization header. If logout ran first, storage would
        // be wiped and the revoke call would go out unauthenticated
        // (or fail to be made at all if the network layer reads the
        // token from storage).
        val sequence = mutableListOf<String>()
        val sequenceTrackingStorage = OrderTrackingTokenStorage { sequence.add(TAG_LOGOUT) }
        val logout =
            LogoutUseCase(
                storage = sequenceTrackingStorage,
                canceller = UnpairNoOpUploadWorkCanceller(),
                resetter = UnpairNoOpLocalDatabaseResetter(),
            )
        val revoker = RecordingDeviceRevoker(result = RevocationResult.Success) { sequence.add(TAG_REVOKE) }
        val useCase = UnpairDeviceUseCase(revoker, logout)

        useCase.unpair()

        assertEquals(listOf(TAG_REVOKE, TAG_LOGOUT), sequence)
    }

    @Test
    fun revoker_is_invoked_exactly_once_per_unpair_call() = runTest {
        // Defensive: a retry inside the use case would either be
        // wrong (re-revoking an already-revoked device gets 401)
        // or pointless (revoke is idempotent server-side). Either
        // way the use case must fire exactly one call per intent.
        val revoker = RecordingDeviceRevoker(result = RevocationResult.Success)
        val logout =
            LogoutUseCase(
                storage = UnpairTokenStorageFake(),
                canceller = UnpairNoOpUploadWorkCanceller(),
                resetter = UnpairNoOpLocalDatabaseResetter(),
            )
        val useCase = UnpairDeviceUseCase(revoker, logout)

        useCase.unpair()

        assertEquals(1, revoker.invocationCount)
    }
}

private class RecordingDeviceRevoker(
    private val result: RevocationResult,
    private val onInvoke: () -> Unit = {},
) : DeviceRevoker {
    var invocationCount: Int = 0

    override suspend fun revokeCurrentDevice(): RevocationResult {
        invocationCount++
        onInvoke()
        return result
    }
}

private class UnpairTokenStorageFake : SecureTokenStorage {
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

/** Records a tag every time clearAll runs — so the order test can
 * verify revoke happened before logout's first storage mutation. */
private class OrderTrackingTokenStorage(
    private val onClearAll: () -> Unit,
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
        onClearAll()
    }
}

private class UnpairNoOpUploadWorkCanceller : UploadWorkCanceller {
    override suspend fun cancelAllUploads() = Unit
}

private class UnpairNoOpLocalDatabaseResetter : LocalDatabaseResetter {
    override suspend fun resetAll() = Unit
}
