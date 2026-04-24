package com.lap.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * RuleBundleEntity caches the compiled coaching-rules bundle that
 * T212 fetches at login. TTL check (T213: 7 days) consults
 * [fetchedAt]; [signature] is verified before the rules-engine
 * runtime (T214) hands a bundle to evaluation. Payload stays as
 * opaque JSON so T211 rule-engine spec changes don't force Room
 * migrations.
 */
class RuleBundleEntityTest {
    @Test
    fun constructs_signed_bundle_row() {
        val bundle = RuleBundleEntity(
            id = "2026.04.1",
            version = "2026.04.1",
            bundleJson = """{"rules":[{"id":"TRAIL_BRAKING_001","applicable_to":["mobile_real_life"]}]}""",
            signature = "sha256:deadbeef",
            fetchedAt = Instant.parse("2026-04-24T10:00:00Z"),
        )
        assertEquals("2026.04.1", bundle.version)
        assertEquals("sha256:deadbeef", bundle.signature)
    }
}

class MigrationsT048Test {
    @org.junit.Test
    fun migration_6_7_declares_correct_version_range() {
        assertEquals(6, MIGRATION_6_7.startVersion)
        assertEquals(7, MIGRATION_6_7.endVersion)
    }
}
