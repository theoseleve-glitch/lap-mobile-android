package com.lap.mobile.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_FILE_NAME: String = "secure_tokens"

/**
 * Android-backed [SecureTokenStorage] using Jetpack Security's
 * [EncryptedSharedPreferences].
 *
 * Encryption layout:
 *  - Master key: AES-256, generated once and stored in the Android Keystore
 *    (hardware-backed on devices with a TEE/StrongBox; software-backed
 *    otherwise — that's an OS-level decision we do not override).
 *  - Per-key encryption: AES256-SIV (deterministic, so two writes of the
 *    same plaintext key produce the same ciphertext key, which lets
 *    SharedPreferences index them).
 *  - Per-value encryption: AES256-GCM (authenticated, randomized — IV is
 *    embedded in the ciphertext blob).
 *
 * Why GCM not CTR: GCM authenticates the ciphertext, so a tampered
 * stored token fails the read instead of silently decrypting to garbage
 * that we'd then send to the server.
 *
 * The `SharedPreferences` underneath is auto-loaded from disk at first
 * `getString` and writes go through a commit/apply cycle. We use
 * `commit()` for synchronous durability — we'd rather a write block for
 * a few ms than have a process death lose the freshly-rotated refresh
 * token (T174). Token writes are infrequent (login + refresh ≈ once per
 * hour) so the perf cost is negligible.
 *
 * Verifying the actual Keystore-rooted encryption is [PENDING:
 * device-required] — EncryptedSharedPreferences depends on real Android
 * Keystore hardware (or software fallback) which JVM unit tests can't
 * exercise. The interface contract is locked in
 * `SecureTokenStorageContractTest`.
 */
@Singleton
class EncryptedPreferencesTokenStorage
@Inject
constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) : SecureTokenStorage {
    private val prefs: SharedPreferences by lazy {
        val masterKey =
            MasterKey
                .Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun read(key: String): String? = prefs.getString(key, null)

    override fun write(key: String, value: String) {
        prefs.edit().putString(key, value).commit()
    }

    override fun clear(key: String) {
        prefs.edit().remove(key).commit()
    }

    override fun clearAll() {
        prefs.edit().clear().commit()
    }
}
