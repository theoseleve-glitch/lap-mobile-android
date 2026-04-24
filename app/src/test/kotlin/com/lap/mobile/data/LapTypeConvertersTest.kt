package com.lap.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * Room stores primitives; these converters let DAOs accept domain types
 * directly. UUID7 is already a [String] typealias so the converter is
 * identity — it exists so future promotion of UUID7 to a value class
 * wouldn't cascade across every DAO. Instant stores as RFC3339 to stay
 * human-readable in the SQLite browser and interoperable with the
 * RFC3339 strings the schemas module serializes.
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

    @Test
    fun uuid7_passes_through_untouched() {
        val id = "0194aaaa-aaaa-7aaa-aaaa-aaaaaaaaaaaa"
        assertEquals(id, converters.fromUuid7(id))
        assertEquals(id, converters.toUuid7(id))
    }

    @Test
    fun uuid7_null_passes_through() {
        assertNull(converters.fromUuid7(null))
        assertNull(converters.toUuid7(null))
    }
}
