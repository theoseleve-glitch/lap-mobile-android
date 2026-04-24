package com.lap.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * TrackCache stores track metadata + sector splits for offline
 * dashboard rendering (trackmap, sector timing). No Track Pydantic
 * schema exists yet — [sectorsJson] is an opaque JSON blob keyed by
 * trackId so the shape can evolve backend-side without a Room
 * migration.
 */
class TrackCacheEntityTest {
    @Test
    fun constructs_row_with_sector_payload() {
        val row = TrackCacheEntity(
            id = "0194aaaa-aaaa-7aaa-aaaa-aaaaaaaaaaaa",
            name = "Spa-Francorchamps",
            layout = "gp",
            sectorsJson = """[{"index":1,"start_m":0,"end_m":2340},{"index":2,"start_m":2340,"end_m":4680}]""",
            cachedAt = Instant.parse("2026-04-24T10:00:00Z"),
        )
        assertEquals("gp", row.layout)
        assertEquals("Spa-Francorchamps", row.name)
    }
}

class MigrationsT047Test {
    @org.junit.Test
    fun migration_5_6_declares_correct_version_range() {
        assertEquals(5, MIGRATION_5_6.startVersion)
        assertEquals(6, MIGRATION_5_6.endVersion)
    }
}
