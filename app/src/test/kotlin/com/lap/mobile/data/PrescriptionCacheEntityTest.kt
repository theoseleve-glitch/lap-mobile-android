package com.lap.mobile.data

import com.lap.schemas.v1.ActionPrescription
import com.lap.schemas.v1.EnvironmentTag
import com.lap.schemas.v1.LapSchemasJson
import com.lap.schemas.v1.SessionContext
import com.lap.schemas.v1.SuccessCondition
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * PrescriptionCache stores backend-authored ActionPrescriptions keyed
 * by driver (not session — prescriptions are longitudinal, may span
 * sessions or have no session attached yet). Payload is kotlinx-
 * serialized JSON (LapSchemasJson) so T035 shape churn stays out of
 * Room migrations.
 */
class PrescriptionCacheEntityTest {
    @Test
    fun constructs_row_with_serialized_payload() {
        val rx = ActionPrescription(
            id = "0194aaaa-aaaa-7aaa-aaaa-aaaaaaaaaaaa",
            driverId = "0194bbbb-bbbb-7bbb-bbbb-bbbbbbbbbbbb",
            createdAt = "2026-04-24T09:00:00Z",
            priority = "p1",
            title = "Steady trail-brake release",
            rationale = "reduce variance in release timing",
            expectedContext = SessionContext(
                carId = "0194cccc-cccc-7ccc-cccc-cccccccccccc",
                trackId = "0194dddd-dddd-7ddd-dddd-dddddddddddd",
                trackLayout = "gp",
                environmentTag = EnvironmentTag.REAL_TRACK_DAY,
            ),
            successCondition = SuccessCondition(
                metric = "release_slope_stddev",
                targetValue = 0.05,
                comparison = "lte",
                measurementWindow = "next_3_sessions",
            ),
            status = "accepted",
        )
        val payload = LapSchemasJson.encodeToString(rx)
        val row = PrescriptionCacheEntity(
            id = rx.id,
            driverId = rx.driverId,
            payload = payload,
            cachedAt = Instant.parse("2026-04-24T10:00:00Z"),
        )

        val decoded = LapSchemasJson.decodeFromString<ActionPrescription>(row.payload)
        assertEquals(rx, decoded)
        assertEquals(rx.driverId, row.driverId)
    }
}

class MigrationsT046Test {
    @org.junit.Test
    fun migration_4_5_declares_correct_version_range() {
        assertEquals(4, MIGRATION_4_5.startVersion)
        assertEquals(5, MIGRATION_4_5.endVersion)
    }
}
