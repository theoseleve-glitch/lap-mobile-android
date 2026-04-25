package com.lap.mobile.auth

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

private const val ACCESS_VALUE: String = "instrumented-access-token-roundtrip"
private const val REFRESH_VALUE: String = "instrumented-refresh-token-roundtrip"

/**
 * T190 instrumented half — exercises [EncryptedPreferencesTokenStorage]
 * against a REAL Android Keystore + EncryptedSharedPreferences. JVM
 * unit tests in [SecureTokenStorageContractTest] use an in-memory fake
 * because Keystore-rooted crypto can't run on a JVM; the AES256-GCM
 * value encryption + MasterKey-via-Keystore path is exercised only
 * here.
 *
 * What this catches that the JVM contract test cannot:
 *   - The `commit()` path actually writes through EncryptedSharedPreferences'
 *     Tink-backed file format.
 *   - MasterKey generation/retrieval works on the device's actual
 *     Keystore (StrongBox if available, TEE otherwise).
 *   - Reads after a process restart would survive (instrumented tests
 *     restart the InstrumentationRegistry context but not the JVM —
 *     a roundtrip in one test still proves the file format is stable).
 *
 * Status: [PENDING: device-required]. The /loop runner has no device
 * connected so this test class isn't executed; the scaffold lands now
 * so a future CI device or a developer running `./gradlew connectedDebugAndroidTest`
 * has the contract locked in code.
 *
 * Running locally:
 *   ./gradlew app:connectedDevDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.lap.mobile.auth.EncryptedTokenStorageInstrumentedTest
 *
 * Each test calls [EncryptedPreferencesTokenStorage.clearAll] in
 * teardown so the SharedPreferences file doesn't carry state between
 * runs (instrumented tests share a single app data dir per package).
 */
@RunWith(AndroidJUnit4::class)
class EncryptedTokenStorageInstrumentedTest {
    private val storage: EncryptedPreferencesTokenStorage by lazy {
        EncryptedPreferencesTokenStorage(context = ApplicationProvider.getApplicationContext())
    }

    @org.junit.After
    fun tearDown() {
        storage.clearAll()
    }

    @Test
    fun write_then_read_round_trips_through_real_keystore_backed_encryption() {
        storage.write(SecureTokenKeys.ACCESS_TOKEN, ACCESS_VALUE)

        assertEquals(ACCESS_VALUE, storage.read(SecureTokenKeys.ACCESS_TOKEN))
    }

    @Test
    fun clear_single_key_doesnt_affect_other_keys_so_t174_silent_refresh_can_rotate_access_independently() {
        storage.write(SecureTokenKeys.ACCESS_TOKEN, ACCESS_VALUE)
        storage.write(SecureTokenKeys.REFRESH_TOKEN, REFRESH_VALUE)

        storage.clear(SecureTokenKeys.ACCESS_TOKEN)

        assertNull(storage.read(SecureTokenKeys.ACCESS_TOKEN))
        assertEquals(REFRESH_VALUE, storage.read(SecureTokenKeys.REFRESH_TOKEN))
    }

    @Test
    fun clear_all_drops_every_key_so_t175_logout_leaves_a_clean_slate() {
        storage.write(SecureTokenKeys.ACCESS_TOKEN, ACCESS_VALUE)
        storage.write(SecureTokenKeys.REFRESH_TOKEN, REFRESH_VALUE)

        storage.clearAll()

        assertNull(storage.read(SecureTokenKeys.ACCESS_TOKEN))
        assertNull(storage.read(SecureTokenKeys.REFRESH_TOKEN))
    }

    @Test
    fun read_unknown_key_returns_null_so_first_launch_login_path_can_distinguish_signed_out_from_have_credentials() {
        assertNull(storage.read(SecureTokenKeys.ACCESS_TOKEN))
    }
}
