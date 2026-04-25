package com.lap.mobile.data

/**
 * What network the upload worker is allowed to use (T156).
 *  - [UNMETERED_ONLY] — Wi-Fi (or another unmetered link). The default.
 *    Protects users from a surprise cellular bill on a multi-hour
 *    bundle upload.
 *  - [ALLOW_METERED] — also use cellular. The user opts in when they
 *    want a session uploaded immediately and don't mind paying for it.
 */
enum class NetworkPolicy { UNMETERED_ONLY, ALLOW_METERED }

/**
 * Whether the upload worker waits for battery to recover from "low"
 * before running (T158).
 *  - [DEFER_WHEN_LOW] — default. WorkManager parks the upload while
 *    the battery is in the "low" bucket so a multi-hour transfer
 *    doesn't strand a near-empty device.
 *  - [FORCE_REGARDLESS] — user opts to push the upload through even
 *    on a low battery; typical when plugged into a tender at the
 *    track and they want the session uploaded before disconnecting.
 */
enum class BatteryPolicy { DEFER_WHEN_LOW, FORCE_REGARDLESS }

/**
 * Read-write surface over the persisted [UploadPreferencesEntity]
 * (T156 + T158). The upload worker reads [networkPolicy] and
 * [batteryPolicy] before scheduling to build the WorkManager
 * constraints; the Settings screen calls the per-toggle setters when
 * the user flips a switch.
 *
 * On a fresh install no row exists yet — both policies return their
 * safe defaults ([NetworkPolicy.UNMETERED_ONLY],
 * [BatteryPolicy.DEFER_WHEN_LOW]) so the first-launch path matches
 * the conservative "WiFi-only, defer on low battery" behaviour
 * exactly. The first setter call inserts the row; subsequent setters
 * preserve the other toggle so flipping one switch never clobbers the
 * other.
 */
class UploadPreferencesRepository(
    private val dao: UploadPreferencesDao,
) {
    suspend fun networkPolicy(): NetworkPolicy =
        if (dao.get()?.allowMetered == true) NetworkPolicy.ALLOW_METERED else NetworkPolicy.UNMETERED_ONLY

    suspend fun batteryPolicy(): BatteryPolicy =
        if (dao.get()?.forceWhenBatteryLow == true) BatteryPolicy.FORCE_REGARDLESS else BatteryPolicy.DEFER_WHEN_LOW

    suspend fun setAllowMetered(allow: Boolean) {
        val current = dao.get()
        dao.upsert(
            UploadPreferencesEntity(
                id = UploadPreferencesEntity.SINGLETON_ID,
                allowMetered = allow,
                forceWhenBatteryLow = current?.forceWhenBatteryLow ?: false,
            ),
        )
    }

    suspend fun setForceWhenBatteryLow(force: Boolean) {
        val current = dao.get()
        dao.upsert(
            UploadPreferencesEntity(
                id = UploadPreferencesEntity.SINGLETON_ID,
                allowMetered = current?.allowMetered ?: false,
                forceWhenBatteryLow = force,
            ),
        )
    }
}
