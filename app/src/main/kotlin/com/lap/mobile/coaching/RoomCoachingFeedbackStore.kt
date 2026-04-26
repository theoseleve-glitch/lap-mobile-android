package com.lap.mobile.coaching

import com.lap.mobile.data.CoachingFeedbackDao
import com.lap.mobile.data.CoachingFeedbackEntity
import java.time.Clock
import javax.inject.Inject

/**
 * Production [CoachingFeedbackStore] backed by Room's
 * [CoachingFeedbackDao] (T219).
 *
 * Maps between the persistence type [CoachingFeedbackEntity]
 * (stored kind as String per Room/enum-converter ergonomics — see
 * entity KDoc) and the public type [CoachingFeedback] (typed kind
 * via [CoachingFeedbackKind] enum).
 *
 * [Clock] injected for deterministic test timing — production
 * binds `Clock.systemUTC()` via UploadModule.provideClock; tests
 * pass [Clock.fixed] to advance time without `Thread.sleep`.
 *
 * Unknown kind values from the database (e.g., a future kind added
 * by a newer client and seen by an older client during a
 * downgrade) collapse to [CoachingFeedbackKind.DISMISSED] as the
 * least-impactful default — the alternative (throw / drop) would
 * lose user feedback during a partial-rollout window. Forward-
 * compat policy mirrors T217's unknown-severity → MEDIUM fallback.
 *
 * Live device verification — actual SQLite persistence under Room
 * KSP-generated impl, schema-version migration to v11, foreground-
 * service Hilt injection — is [PENDING: device-required]. Store
 * LOGIC (kind round-trip, ordering, clear semantics) is JVM-tested
 * via the fake DAO.
 */
class RoomCoachingFeedbackStore
@Inject
constructor(
    private val dao: CoachingFeedbackDao,
    private val clock: Clock,
) : CoachingFeedbackStore {
    override suspend fun recordDismissal(ruleId: String) {
        dao.insert(
            CoachingFeedbackEntity(
                ruleId = ruleId,
                kind = CoachingFeedbackKind.DISMISSED.name,
                recordedAt = clock.instant(),
            ),
        )
    }

    override suspend fun recordFlaggedWrong(ruleId: String) {
        dao.insert(
            CoachingFeedbackEntity(
                ruleId = ruleId,
                kind = CoachingFeedbackKind.FLAGGED_WRONG.name,
                recordedAt = clock.instant(),
            ),
        )
    }

    override suspend fun loadAll(): List<CoachingFeedback> = dao.loadAll().map { entity ->
        CoachingFeedback(
            ruleId = entity.ruleId,
            kind = entity.kind.toFeedbackKindOrDefault(),
            recordedAt = entity.recordedAt,
        )
    }

    override suspend fun clear() {
        dao.clear()
    }

    @Suppress("SwallowedException")
    private fun String.toFeedbackKindOrDefault(): CoachingFeedbackKind = try {
        CoachingFeedbackKind.valueOf(this)
    } catch (e: IllegalArgumentException) {
        // Forward-compat: unknown kind from a newer client during
        // downgrade → least-impactful default (DISMISSED). Don't
        // drop the record entirely; the rule_id + timestamp are
        // still useful signal even if the kind is mis-attributed.
        // @Suppress documents the intentional swallow for detekt.
        CoachingFeedbackKind.DISMISSED
    }
}
