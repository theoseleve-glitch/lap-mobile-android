package com.lap.mobile.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/**
 * DAO for the [RuleBundleEntity] cache (T213).
 *
 * Two operations only — upsert (save) and loadLatest (read-most-
 * recent). The cache contract (T211 [com.lap.mobile.coaching.RuleBundleCache])
 * is binary: there's either a current bundle or there isn't. No
 * "list all" / "get by id" surface because callers always want
 * the freshest, and historical entries are pure noise that
 * drift `rule_bundle` table size unbounded over time.
 *
 * `loadLatest` orders by `fetched_at DESC LIMIT 1` so a server
 * mid-rollout that ships two bundles in quick succession produces
 * the most recently *fetched* result (not the most recently
 * *compiled*) — the device acted on a specific bundle at fetch
 * time and that's what should be served back. Compile-order
 * tiebreaks happen server-side via the `compiled_at` field that
 * the resolution orchestrator (T211 BundleResolution) compares.
 *
 * Tests can implement this interface directly with an in-memory
 * `MutableMap<String, RuleBundleEntity>` — see
 * [com.lap.mobile.coaching.RoomRuleBundleCacheTest]'s
 * `FakeRuleBundleDao`. Bypassing Room's KSP-generated impl in JVM
 * tests means no Robolectric, no Android dependency, and the
 * cache logic gets exercised without the SQLite layer.
 */
@Dao
interface RuleBundleDao {
    @Upsert
    suspend fun upsert(entity: RuleBundleEntity)

    @Query("SELECT * FROM rule_bundle ORDER BY fetched_at DESC LIMIT 1")
    suspend fun loadLatest(): RuleBundleEntity?
}
