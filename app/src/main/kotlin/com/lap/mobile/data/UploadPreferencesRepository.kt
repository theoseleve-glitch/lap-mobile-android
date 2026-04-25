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
 * Read-write surface over the persisted [UploadPreferencesEntity]
 * (T156). The upload worker reads [networkPolicy] before scheduling
 * to decide whether the WorkManager constraint is `UNMETERED` or
 * `CONNECTED`; the Settings screen calls [setAllowMetered] when the
 * user flips the toggle.
 *
 * On a fresh install no row exists yet — [networkPolicy] returns the
 * safe default ([NetworkPolicy.UNMETERED_ONLY]) so the first-launch
 * path matches "user opted into Wi-Fi only" exactly. No row is
 * inserted until [setAllowMetered] is first called.
 */
class UploadPreferencesRepository(
    private val dao: UploadPreferencesDao,
) {
    suspend fun networkPolicy(): NetworkPolicy =
        if (dao.get()?.allowMetered == true) NetworkPolicy.ALLOW_METERED else NetworkPolicy.UNMETERED_ONLY

    suspend fun setAllowMetered(allow: Boolean) {
        dao.upsert(
            UploadPreferencesEntity(
                id = UploadPreferencesEntity.SINGLETON_ID,
                allowMetered = allow,
            ),
        )
    }
}
