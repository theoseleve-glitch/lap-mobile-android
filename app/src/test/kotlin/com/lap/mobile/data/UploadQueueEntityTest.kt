package com.lap.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * Locks the UploadQueueEntity shape. Real Room round-trip lands in
 * instrumented tests (T083+); this only covers what we can verify in
 * JVM: constructor shape, sensible defaults for fresh enqueue.
 */
class UploadQueueEntityTest {
    @Test
    fun fresh_enqueue_starts_in_queued_state_with_zero_attempts() {
        val row = UploadQueueEntity(
            sessionId = "0194aaaa-aaaa-7aaa-aaaa-aaaaaaaaaaaa",
            state = "queued",
            chunksTotal = 40,
            idempotencyKey = "0194aaaa-aaaa-7aaa-aaaa-aaaaaaaaaaaa",
        )
        assertEquals("queued", row.state)
        assertEquals(0, row.attemptCount)
        assertEquals(0, row.chunksSent)
        assertEquals(40, row.chunksTotal)
        assertNull(row.nextRetryAt)
        assertNull(row.lastHttpCode)
        assertNull(row.lastError)
    }

    @Test
    fun captures_server_5xx_for_backoff() {
        val row = UploadQueueEntity(
            sessionId = "0194bbbb-bbbb-7bbb-bbbb-bbbbbbbbbbbb",
            state = "failed",
            attemptCount = 3,
            nextRetryAt = Instant.parse("2026-04-24T10:05:00Z"),
            lastHttpCode = 502,
            lastError = "Bad Gateway",
            chunksSent = 12,
            chunksTotal = 40,
            idempotencyKey = "0194bbbb-bbbb-7bbb-bbbb-bbbbbbbbbbbb",
        )
        assertEquals(502, row.lastHttpCode)
        assertEquals(12, row.chunksSent)
    }
}

class MigrationsT044Test {
    @org.junit.Test
    fun migration_2_3_declares_correct_version_range() {
        assertEquals(2, MIGRATION_2_3.startVersion)
        assertEquals(3, MIGRATION_2_3.endVersion)
    }
}
