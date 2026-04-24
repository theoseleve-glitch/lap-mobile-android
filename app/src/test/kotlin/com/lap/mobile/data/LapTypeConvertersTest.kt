package com.lap.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * Room stores primitives; these converters let DAOs accept domain types
 * directly. Instant stores as RFC3339 to stay interoperable with the
 * strings [com.lap.schemas.v1.LapSchemasJson] produces (ADR-012) and
 * human-readable in SQLite browsers. UUID7 has no converter — it's a
 * String typealias, so Room persists it natively.
 */
class LapTypeConvertersTest {
    private val converters = LapTypeConverters()

    @Test
    fun instant_round_trips_through_rfc3339_string() {
        val instant = Instant.parse("2026-04-24T10:15:30.123456789Z")
        val stored = converters.fromInstant(instant)
        assertEquals("2026-04-24T10:15:30.123456789Z", stored)
        assertEquals(instant, converters.toInstant(stored))
    }

    @Test
    fun instant_null_is_preserved_both_directions() {
        assertNull(converters.fromInstant(null))
        assertNull(converters.toInstant(null))
    }
}
