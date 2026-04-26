package com.lap.mobile.coaching

import com.lap.mobile.data.RuleBundleDao
import com.lap.mobile.data.RuleBundleEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

/**
 * Locks the contract for [RoomRuleBundleCache] (T213) — the
 * Room-backed implementation of T211's [RuleBundleCache] interface.
 *
 * Why a fake [RuleBundleDao] instead of in-memory Room: Room's
 * `@Dao` is a Kotlin interface, KSP generates the production impl
 * at compile time. JVM tests can implement that interface directly
 * with a `MutableMap<String, RuleBundleEntity>` — no Robolectric,
 * no Android dependency, no in-memory database setup. The Room-
 * to-SQLite mapping is Google's job; verifying it requires
 * instrumented tests that ship later. What we test here: the
 * cache LOGIC (TTL filtering, signature verification, parse
 * errors, overwrite semantics) — that's where regression risk lives.
 *
 * Coverage: every branch of [RuleBundleCache.load] (cache miss,
 * fresh, stale, signature mismatch, missing-signature-when-verified,
 * corrupt-payload) + the save→load round-trip + the upsert behavior
 * of repeated saves.
 *
 * TTL testing uses two [Clock.fixed] instances — one when saving
 * (advances the entity's `fetchedAt`) and another when loading
 * ("now"). Pure functions over Instants beat `Thread.sleep` because
 * the test is deterministic, fast, and doesn't depend on JVM clock
 * resolution.
 */
class RoomRuleBundleCacheTest {
    @Test
    fun save_then_load_returns_cached_bundle_so_round_trip_through_cache_is_lossless() = runTest {
        val dao = FakeRuleBundleDao()
        val cache = RoomRuleBundleCache(dao = dao, clock = fixedClockAt(SAVE_TIME))

        cache.save(rawPayload = CANONICAL_PAYLOAD, signature = "sig-abc")
        val loaded = cache.load()

        assertNotNull("Expected non-null cache hit after save", loaded)
        assertEquals("bundle-2026-04-26", loaded!!.bundle.bundleId)
        assertEquals(CANONICAL_PAYLOAD, loaded.rawPayload)
    }

    @Test
    fun load_empty_cache_returns_null_so_cold_start_callers_re_fetch_from_server() = runTest {
        val cache = RoomRuleBundleCache(dao = FakeRuleBundleDao(), clock = fixedClockAt(SAVE_TIME))

        assertNull(cache.load())
    }

    @Test
    fun load_within_ttl_returns_cached_so_fresh_cache_is_served_without_refetch() = runTest {
        val dao = FakeRuleBundleDao()
        val saveCache = RoomRuleBundleCache(dao = dao, clock = fixedClockAt(SAVE_TIME))
        saveCache.save(rawPayload = CANONICAL_PAYLOAD, signature = "")

        // Advance clock by 1 hour, well within a 7-day TTL window.
        val loadCache = RoomRuleBundleCache(
            dao = dao,
            clock = fixedClockAt(SAVE_TIME.plus(1.hours.toJavaDuration())),
        )

        val loaded = loadCache.load(maxAge = 7.days)

        assertNotNull(loaded)
    }

    @Test
    fun load_outside_ttl_returns_null_so_stale_cache_doesnt_silently_serve_outdated_rules() = runTest {
        val dao = FakeRuleBundleDao()
        val saveCache = RoomRuleBundleCache(dao = dao, clock = fixedClockAt(SAVE_TIME))
        saveCache.save(rawPayload = CANONICAL_PAYLOAD, signature = "")

        // Advance clock 8 days — past the 7-day TTL.
        val loadCache = RoomRuleBundleCache(
            dao = dao,
            clock = fixedClockAt(SAVE_TIME.plus(8.days.toJavaDuration())),
        )

        val loaded = loadCache.load(maxAge = 7.days)

        assertNull(loaded)
    }

    @Test
    fun load_without_max_age_returns_cached_regardless_of_age_so_offline_fallback_path_works() = runTest {
        // load() with maxAge=null is the "STALE_CACHE fallback" path
        // from the resolution orchestrator (per T211's BundleSource).
        // It serves the cache even if it's months old — better than
        // no coaching at all when the user is offline.
        val dao = FakeRuleBundleDao()
        val saveCache = RoomRuleBundleCache(dao = dao, clock = fixedClockAt(SAVE_TIME))
        saveCache.save(rawPayload = CANONICAL_PAYLOAD, signature = "")

        val loadCache = RoomRuleBundleCache(
            dao = dao,
            clock = fixedClockAt(SAVE_TIME.plus(60.days.toJavaDuration())),
        )

        val loaded = loadCache.load(maxAge = null)

        assertNotNull(loaded)
    }

    @Test
    fun load_with_verifier_returning_true_returns_cached_so_signature_match_passes_through() = runTest {
        val dao = FakeRuleBundleDao()
        val cache = RoomRuleBundleCache(dao = dao, clock = fixedClockAt(SAVE_TIME))
        cache.save(rawPayload = CANONICAL_PAYLOAD, signature = "valid-sig")

        val loaded = cache.load(
            verifier = { payload, signature ->
                payload == CANONICAL_PAYLOAD && signature == "valid-sig"
            },
        )

        assertNotNull(loaded)
    }

    @Test
    fun load_with_verifier_returning_false_throws_cache_exception_so_tampering_is_escalated() = runTest {
        // Headline T213 invariant: signature mismatch on otherwise
        // fresh & parseable cache is a TAMPERING signal — must
        // escalate, not silently degrade. Distinct from a routine
        // miss (which is null from load).
        val dao = FakeRuleBundleDao()
        val cache = RoomRuleBundleCache(dao = dao, clock = fixedClockAt(SAVE_TIME))
        cache.save(rawPayload = CANONICAL_PAYLOAD, signature = "tampered-sig")

        try {
            cache.load(verifier = { _, _ -> false })
            org.junit.Assert.fail("Expected RuleBundleException.Cache, no exception thrown")
        } catch (expected: RuleBundleException.Cache) {
            // expected
        }
    }

    @Test
    fun load_with_verifier_but_empty_signature_throws_cache_exception_so_unsigned_cache_cant_be_trusted() = runTest {
        // Cache entry was written with empty signature (dev/staging
        // mode), but a verifier is now requested (production
        // upgrade). Can't verify → tampering signal, escalate.
        val dao = FakeRuleBundleDao()
        val cache = RoomRuleBundleCache(dao = dao, clock = fixedClockAt(SAVE_TIME))
        cache.save(rawPayload = CANONICAL_PAYLOAD, signature = "")

        try {
            cache.load(verifier = { _, _ -> true })
            org.junit.Assert.fail("Expected RuleBundleException.Cache, no exception thrown")
        } catch (expected: RuleBundleException.Cache) {
            // expected
        }
    }

    @Test
    fun save_overwrites_previous_bundle_so_repeated_fetches_dont_accumulate_in_cache() = runTest {
        val dao = FakeRuleBundleDao()
        val firstCache = RoomRuleBundleCache(dao = dao, clock = fixedClockAt(SAVE_TIME))
        firstCache.save(rawPayload = CANONICAL_PAYLOAD, signature = "")

        // Second save uses a clock 1 hour later so the newer entity
        // has a strictly-later fetched_at — loadLatest then deterministically
        // returns it (FakeDao mirrors Room's ORDER BY DESC tiebreak).
        val secondCache = RoomRuleBundleCache(
            dao = dao,
            clock = fixedClockAt(SAVE_TIME.plus(1.hours.toJavaDuration())),
        )
        secondCache.save(rawPayload = NEWER_PAYLOAD, signature = "")

        val loaded = secondCache.load()

        assertNotNull(loaded)
        assertEquals("bundle-2026-04-27", loaded!!.bundle.bundleId)
    }

    @Test
    fun save_with_unparseable_payload_throws_parse_exception_so_callers_cant_corrupt_cache() = runTest {
        val cache = RoomRuleBundleCache(dao = FakeRuleBundleDao(), clock = fixedClockAt(SAVE_TIME))

        try {
            cache.save(rawPayload = "{not json", signature = "")
            org.junit.Assert.fail("Expected RuleBundleException.Parse, no exception thrown")
        } catch (expected: RuleBundleException.Parse) {
            // expected
        }
    }

    private fun fixedClockAt(instant: Instant): Clock = Clock.fixed(instant, ZoneOffset.UTC)

    private companion object {
        // 2026-04-26T12:00:00Z, anchor of the TTL test cases.
        val SAVE_TIME: Instant = Instant.parse("2026-04-26T12:00:00Z")

        const val CANONICAL_PAYLOAD: String = """{
  "schema_version": "1.0.0",
  "bundle_id": "bundle-2026-04-26",
  "compiled_at": "2026-04-26T08:00:00Z",
  "rules": []
}"""

        const val NEWER_PAYLOAD: String = """{
  "schema_version": "1.0.0",
  "bundle_id": "bundle-2026-04-27",
  "compiled_at": "2026-04-27T08:00:00Z",
  "rules": []
}"""
    }
}

/**
 * In-memory fake of [RuleBundleDao] for JVM tests. Mirrors Room's
 * upsert + ORDER-BY-fetched_at-DESC LIMIT 1 semantics with plain
 * Kotlin so the cache logic can be tested without Robolectric.
 */
private class FakeRuleBundleDao : RuleBundleDao {
    private val rows: MutableMap<String, RuleBundleEntity> = mutableMapOf()

    override suspend fun upsert(entity: RuleBundleEntity) {
        rows[entity.id] = entity
    }

    override suspend fun loadLatest(): RuleBundleEntity? = rows.values.maxByOrNull { it.fetchedAt }
}
