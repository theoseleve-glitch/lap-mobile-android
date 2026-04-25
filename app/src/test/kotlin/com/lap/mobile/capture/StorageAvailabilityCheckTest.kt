package com.lap.mobile.capture

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val MEGABYTE: Long = 1024L * 1024L
private const val MIN_FREE_BYTES: Long = 500L * MEGABYTE
private const val UNDER_BY_ONE_MB: Long = MIN_FREE_BYTES - MEGABYTE
private const val WAY_OVER_BYTES: Long = 8L * 1024L * MEGABYTE

/**
 * Unit tests for [StorageAvailabilityCheck] (T093). Hard-blocks
 * recording if the volume the [com.lap.mobile.data.NdjsonBufferSink]
 * writes into has < 500 MB free — the expected per-hour bundle is
 * ~250 MB raw NDJSON, so 500 MB is roughly two hours of headroom
 * before the sink would start failing.
 *
 * Boundary equality (`>= minFreeBytes`) passes — 500 MB exactly is
 * acceptable.
 */
class StorageAvailabilityCheckTest {
    @Test
    fun more_than_minimum_returns_null() = runTest {
        val status = StorageStatus { WAY_OVER_BYTES }
        val check = StorageAvailabilityCheck(status)

        assertNull(check.failureReason())
    }

    @Test
    fun exactly_at_minimum_returns_null() = runTest {
        val status = StorageStatus { MIN_FREE_BYTES }
        val check = StorageAvailabilityCheck(status)

        assertNull("exactly 500 MB is the boundary — must pass", check.failureReason())
    }

    @Test
    fun below_minimum_returns_storage_low_reason() = runTest {
        val status = StorageStatus { UNDER_BY_ONE_MB }
        val check = StorageAvailabilityCheck(status)

        assertEquals(StorageAvailabilityCheck.REASON_STORAGE_LOW, check.failureReason())
    }

    @Test
    fun zero_free_bytes_returns_storage_low() = runTest {
        val status = StorageStatus { 0L }
        val check = StorageAvailabilityCheck(status)

        assertEquals(StorageAvailabilityCheck.REASON_STORAGE_LOW, check.failureReason())
    }
}
