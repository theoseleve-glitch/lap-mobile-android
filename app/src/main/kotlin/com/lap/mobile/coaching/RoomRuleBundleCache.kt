package com.lap.mobile.coaching

import com.lap.mobile.core.lapJson
import com.lap.mobile.data.RuleBundleDao
import com.lap.mobile.data.RuleBundleEntity
import kotlinx.serialization.SerializationException
import java.time.Clock
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Production [RuleBundleCache] backed by Room's [RuleBundleDao]
 * (T213).
 *
 * Implements T211's contract:
 *   - [save] writes the rawPayload + signature with a fetchedAt
 *     timestamp. Parses the payload to extract `bundleId` for the
 *     entity primary key. Bad payload → [RuleBundleException.Parse]
 *     (defensive — T212's fetcher already parses, so this is
 *     protection against logic errors).
 *   - [load] returns the most recent cache entry, filtered by
 *     optional [Duration] TTL and optional signature [verifier].
 *     Missing / corrupt / TTL-expired collapse to `null` (cold-
 *     start by re-fetching). Signature mismatch on otherwise-
 *     fresh-and-parseable cache throws [RuleBundleException.Cache]
 *     (tampering signal worth escalating, NOT a routine miss).
 *
 * Why parse-in-save: Room needs a primary key. Using `bundleId`
 * as PK means duplicate fetches overwrite (the desktop spec stores
 * a single file at a fixed path; Room equivalent is a single row
 * because subsequent saves upsert by `id`). The alternative —
 * fixed sentinel PK — would store one row per bundleId
 * (unbounded growth) or require a manual delete-then-insert pattern.
 *
 * Raw payload preserved verbatim through [RuleBundleEntity.bundleJson]
 * so signature verification works byte-for-byte (HMAC over re-
 * encoded JSON would break if key ordering or whitespace
 * differed across encoders).
 *
 * [Clock] injected for deterministic TTL testing — production
 * binds `Clock.systemUTC()` (per UploadModule.provideClock); tests
 * pass [Clock.fixed] instances to advance time without
 * `Thread.sleep`.
 *
 * Live device verification — actual SQLite persistence under Room
 * KSP-generated impl, foreground-service Hilt injection, schema-
 * version migration when we eventually add columns — is [PENDING:
 * device-required]. Cache LOGIC (TTL filtering, signature checks,
 * parse handling, save semantics) is JVM-tested via the fake DAO.
 */
class RoomRuleBundleCache
@Inject
constructor(
    private val dao: RuleBundleDao,
    private val clock: Clock,
) : RuleBundleCache {
    override suspend fun save(rawPayload: String, signature: String) {
        val bundle = parseOrThrow(rawPayload)
        val entity = RuleBundleEntity(
            id = bundle.bundleId,
            version = bundle.schemaVersion,
            bundleJson = rawPayload,
            signature = signature,
            fetchedAt = clock.instant(),
        )
        dao.upsert(entity)
    }

    @Suppress("SwallowedException")
    override suspend fun load(
        maxAge: Duration?,
        verifier: ((rawPayload: String, signature: String) -> Boolean)?,
    ): CachedBundle? {
        val entity = dao.loadLatest() ?: return null

        if (maxAge != null) {
            val age = java.time.Duration.between(entity.fetchedAt, clock.instant())
            if (age > maxAge.toJavaDuration()) {
                return null
            }
        }

        if (verifier != null) {
            if (entity.signature.isEmpty()) {
                throw RuleBundleException.Cache(
                    "cache entry has no signature but verification was requested",
                )
            }
            if (!verifier(entity.bundleJson, entity.signature)) {
                throw RuleBundleException.Cache(
                    "cache signature did not verify — possible tampering",
                )
            }
        }

        val bundle = try {
            lapJson.decodeFromString<RuleBundle>(entity.bundleJson)
        } catch (e: SerializationException) {
            // Corrupt cache → cold-start by re-fetching. Don't
            // throw — that would surface as user-visible error
            // when the recovery is automatic. The exception
            // detail is intentionally not propagated; corruption
            // here is a routine signal indistinguishable from
            // a cold-start miss to the orchestrator. @Suppress
            // on the function head documents this for detekt.
            return null
        }

        return CachedBundle(
            bundle = bundle,
            rawPayload = entity.bundleJson,
            cachedAt = entity.fetchedAt.toString(),
        )
    }

    private fun parseOrThrow(rawPayload: String): RuleBundle = try {
        lapJson.decodeFromString<RuleBundle>(rawPayload)
    } catch (e: SerializationException) {
        throw RuleBundleException.Parse(
            "save called with unparseable bundle payload: ${e.message}",
            cause = e,
        )
    }
}
