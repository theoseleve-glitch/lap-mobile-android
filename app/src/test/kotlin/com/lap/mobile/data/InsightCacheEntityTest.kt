package com.lap.mobile.data

import com.lap.schemas.v1.CoachingInsight
import com.lap.schemas.v1.LapSchemasJson
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * InsightCache stores backend-authored CoachingInsights keyed by session
 * for offline dashboard view. The payload is kotlinx-serialized JSON
 * (LapSchemasJson) so T036 shape changes don't force schema migrations
 * — only the envelope columns are SQL-level.
 */
class InsightCacheEntityTest {
    @Test
    fun constructs_row_with_serialized_payload() {
        val insight = CoachingInsight(
            id = "0194aaaa-aaaa-7aaa-aaaa-aaaaaaaaaaaa",
            sessionId = "0194bbbb-bbbb-7bbb-bbbb-bbbbbbbbbbbb",
            driverId = "0194cccc-cccc-7ccc-cccc-cccccccccccc",
            observation = "late apex T4",
            likelyCause = "steering saturated",
            action = "shift 5m earlier",
            confidence = 0.74,
            severity = "MED",
            displayLevel = "INTERMEDIATE",
        )
        val payload = LapSchemasJson.encodeToString(insight)
        val row = InsightCacheEntity(
            id = insight.id,
            sessionId = insight.sessionId,
            driverId = insight.driverId,
            payload = payload,
            cachedAt = Instant.parse("2026-04-24T10:00:00Z"),
        )

        val decoded = LapSchemasJson.decodeFromString<CoachingInsight>(row.payload)
        assertEquals(insight, decoded)
        assertEquals(insight.sessionId, row.sessionId)
    }
}

class MigrationsT045Test {
    @org.junit.Test
    fun migration_3_4_declares_correct_version_range() {
        assertEquals(3, MIGRATION_3_4.startVersion)
        assertEquals(4, MIGRATION_3_4.endVersion)
    }
}
