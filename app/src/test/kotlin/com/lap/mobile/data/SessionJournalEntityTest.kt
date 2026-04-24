package com.lap.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * Journal rows track in-progress capture so startup reconciliation
 * (T055) can detect crashed sessions via stale heartbeats. Unit test
 * only covers constructor shape — real Room round-trip lands in the
 * instrumented tests from T083 onward.
 */
class SessionJournalEntityTest {
    @Test
    fun constructs_fresh_journal_with_zero_offset() {
        val now = Instant.parse("2026-04-24T09:00:00Z")
        val entry = SessionJournalEntity(
            sessionId = "0194aaaa-aaaa-7aaa-aaaa-aaaaaaaaaaaa",
            lastHeartbeatAt = now,
            status = "recording",
        )
        assertEquals(0L, entry.fileOffset)
        assertEquals(now, entry.lastHeartbeatAt)
        assertEquals("recording", entry.status)
    }

    @Test
    fun captures_advancing_file_offset() {
        val entry = SessionJournalEntity(
            sessionId = "0194bbbb-bbbb-7bbb-bbbb-bbbbbbbbbbbb",
            lastHeartbeatAt = Instant.parse("2026-04-24T09:10:00Z"),
            fileOffset = 4_194_304L,
            status = "recording",
        )
        assertEquals(4_194_304L, entry.fileOffset)
    }
}
