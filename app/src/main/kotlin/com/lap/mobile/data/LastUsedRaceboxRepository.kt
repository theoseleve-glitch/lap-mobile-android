package com.lap.mobile.data

import java.time.Instant

/**
 * Domain projection of the persisted "last used RaceBox" row (T124).
 * Distinct from [com.lap.mobile.capture.ble.RaceboxAdvertiser] because
 * the saved record carries no live RSSI — only the identity bits the
 * auto-reconnect path (T125) needs to filter scan results against.
 */
data class LastUsedRacebox(
    val address: String,
    val name: String?,
    val savedAt: Instant,
)

/**
 * Repository over [LastUsedRaceboxDao]. The "tap advertiser → connect →
 * remember" UX (T124) calls [saveSuccessfulPairing] **after** the GATT
 * handshake confirms — never on selection alone — so a failed pair
 * doesn't poison the next session's auto-reconnect.
 *
 * The [now] supplier is injectable so tests can pin `savedAt` without
 * a wall-clock dependency; production wires `Instant::now`.
 *
 * Always uses the singleton row id ([LastUsedRaceboxEntity.SINGLETON_ID])
 * — at most one persisted target per app install. Re-saving overwrites.
 */
class LastUsedRaceboxRepository(
    private val dao: LastUsedRaceboxDao,
    private val now: () -> Instant = Instant::now,
) {
    suspend fun saveSuccessfulPairing(address: String, name: String?) {
        dao.upsert(
            LastUsedRaceboxEntity(
                id = LastUsedRaceboxEntity.SINGLETON_ID,
                address = address,
                name = name,
                savedAt = now(),
            ),
        )
    }

    suspend fun get(): LastUsedRacebox? = dao.get()?.let {
        LastUsedRacebox(address = it.address, name = it.name, savedAt = it.savedAt)
    }

    suspend fun clear() {
        dao.clear()
    }
}
