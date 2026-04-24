package com.lap.mobile.data

import com.lap.schemas.v1.SourceTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * Locks the SessionEntity shape. A real Room round-trip needs an
 * Android runtime (instrumented test, T083 onward); this unit test
 * only covers what's verifiable in JVM: constructor shape, defaults,
 * and the SourceTag converter Room wires in.
 */
class SessionEntityTest {
    @Test
    fun constructs_minimum_session_in_idle_state() {
        val entity = SessionEntity(
            id = "0194aaaa-aaaa-7aaa-aaaa-aaaaaaaaaaaa",
            state = "IDLE",
            source = SourceTag.REAL,
        )
        assertEquals("IDLE", entity.state)
        assertEquals(SourceTag.REAL, entity.source)
        assertNull(entity.startedAt)
        assertNull(entity.endedAt)
        assertNull(entity.trackId)
        assertNull(entity.carId)
        assertEquals(0, entity.sampleCount)
        assertNull(entity.fileUri)
    }

    @Test
    fun constructs_completed_session_with_all_fields() {
        val entity = SessionEntity(
            id = "0194bbbb-bbbb-7bbb-bbbb-bbbbbbbbbbbb",
            state = "COMPLETED",
            startedAt = Instant.parse("2026-04-24T09:00:00Z"),
            endedAt = Instant.parse("2026-04-24T09:45:00Z"),
            source = SourceTag.IRACING,
            trackId = "0194cccc-cccc-7ccc-cccc-cccccccccccc",
            carId = "0194dddd-dddd-7ddd-dddd-dddddddddddd",
            sampleCount = 27_000,
            fileUri = "file:///data/user/0/com.lap.mobile/files/sessions/0194bbbb.bundle",
        )
        assertEquals(27_000, entity.sampleCount)
        assertEquals(SourceTag.IRACING, entity.source)
    }

    @Test
    fun source_tag_converter_round_trips() {
        val converters = LapTypeConverters()
        val stored = converters.fromSourceTag(SourceTag.RF2)
        assertEquals("rf2", stored)
        assertEquals(SourceTag.RF2, converters.toSourceTag(stored))
    }

    @Test
    fun source_tag_converter_handles_null() {
        val converters = LapTypeConverters()
        assertNull(converters.fromSourceTag(null))
        assertNull(converters.toSourceTag(null))
    }
}
