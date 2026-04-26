package com.lap.mobile.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * One user-feedback record on a coaching prompt (T219).
 *
 * Two `kind` values today:
 *   - `"DISMISSED"`: user tapped the Dismiss button without flagging.
 *     Useful as a "tip seen but didn't act" signal for server-side
 *     rule tuning (rules dismissed quickly may be too noisy).
 *   - `"FLAGGED_WRONG"`: user tapped the Flag-as-wrong button. Strong
 *     "this rule shouldn't have fired" feedback — server uses this
 *     to suppress / re-tune the offending rule.
 *
 * Why the auto-incremented `id` PK rather than a composite of
 * (ruleId, recordedAt): a user can flag the same rule on multiple
 * sessions and each event must persist. A composite PK with
 * recordedAt would still distinguish them but adds query complexity
 * for what's effectively an append-only log.
 *
 * Indexed on `recorded_at` so the future feedback uploader (probably
 * bundled with the next /api/v1/feedback endpoint task) can stream
 * records in chronological order without a full table scan.
 *
 * Stored kind as String not enum because Room doesn't natively
 * understand Kotlin enums without a custom converter and the
 * conversion contract should be local to the [CoachingFeedbackStore]
 * implementation (which maps to/from [com.lap.mobile.coaching.CoachingFeedbackKind]).
 *
 * No FK to sessions — feedback outlives any individual session and
 * the rule_id is the natural correlation key with the rule library.
 */
@Entity(
    tableName = "coaching_feedback",
    indices = [Index(value = ["recorded_at"])],
)
data class CoachingFeedbackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "rule_id")
    val ruleId: String,
    @ColumnInfo(name = "kind")
    val kind: String,
    @ColumnInfo(name = "recorded_at")
    val recordedAt: Instant,
)
