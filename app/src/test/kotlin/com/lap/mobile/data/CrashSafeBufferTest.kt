package com.lap.mobile.data

import com.lap.schemas.v1.TelemetrySample
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SOURCE_CLOCK_DEVICE: String = "device_monotonic"
private const val MAX_BATCH_SIZE: Int = 500
private const val FLUSH_INTERVAL_MS: Long = 10_000L
private const val UNDER_BATCH: Int = 499
private const val OVER_BATCH: Int = 501
private const val PARTIAL_BATCH: Int = 250
private const val SAMPLE_NS_BASE: Long = 1_000L
private const val THREE_INTERVALS_MS: Long = 30_000L

/**
 * Unit tests for [CrashSafeBuffer] (T079). Exercises both flush
 * triggers — size threshold inside `enqueue` and the periodic ticker
 * inside `start` — under `runTest` virtual time so the 10 s cadence
 * fires in microseconds of wall time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CrashSafeBufferTest {
    @Test
    fun enqueue_below_max_batch_size_does_not_flush() = runTest {
        val sink = RecordingBufferSink()
        val buf = CrashSafeBuffer(sink)

        repeat(UNDER_BATCH) { i -> buf.enqueue(sample(i.toLong())) }

        assertTrue("size threshold not reached → no flush", sink.batches.isEmpty())
    }

    @Test
    fun enqueue_at_max_batch_size_flushes_one_batch_of_500() = runTest {
        val sink = RecordingBufferSink()
        val buf = CrashSafeBuffer(sink)

        repeat(MAX_BATCH_SIZE) { i -> buf.enqueue(sample(i.toLong())) }

        assertEquals(1, sink.batches.size)
        assertEquals(MAX_BATCH_SIZE, sink.batches[0].size)
    }

    @Test
    fun enqueue_beyond_max_keeps_overflow_buffered_until_next_threshold() = runTest {
        val sink = RecordingBufferSink()
        val buf = CrashSafeBuffer(sink)

        repeat(OVER_BATCH) { i -> buf.enqueue(sample(i.toLong())) }

        assertEquals("first batch only — 1 sample still queued", 1, sink.batches.size)
        assertEquals(MAX_BATCH_SIZE, sink.batches[0].size)
    }

    @Test
    fun flush_now_writes_pending_and_clears_buffer() = runTest {
        val sink = RecordingBufferSink()
        val buf = CrashSafeBuffer(sink)
        repeat(PARTIAL_BATCH) { i -> buf.enqueue(sample(i.toLong())) }

        buf.flushNow()

        assertEquals(1, sink.batches.size)
        assertEquals(PARTIAL_BATCH, sink.batches[0].size)

        // Second flush is a no-op — buffer was drained.
        buf.flushNow()
        assertEquals("no second batch from empty buffer", 1, sink.batches.size)
    }

    @Test
    fun flush_now_with_empty_buffer_writes_nothing() = runTest {
        val sink = RecordingBufferSink()
        val buf = CrashSafeBuffer(sink)

        buf.flushNow()

        assertTrue("empty flush must not invoke sink", sink.batches.isEmpty())
    }

    @Test
    fun periodic_ticker_flushes_pending_after_interval() = runTest {
        val sink = RecordingBufferSink()
        val buf = CrashSafeBuffer(sink)
        val job = buf.start(backgroundScope)
        repeat(PARTIAL_BATCH) { i -> buf.enqueue(sample(i.toLong())) }
        runCurrent()
        assertEquals("nothing flushed before interval", 0, sink.batches.size)

        advanceTimeBy(FLUSH_INTERVAL_MS)
        runCurrent()

        assertEquals(1, sink.batches.size)
        assertEquals(PARTIAL_BATCH, sink.batches[0].size)
        job.cancel()
    }

    @Test
    fun periodic_ticker_does_not_write_when_buffer_is_empty() = runTest {
        val sink = RecordingBufferSink()
        val buf = CrashSafeBuffer(sink)
        val job = buf.start(backgroundScope)

        advanceTimeBy(THREE_INTERVALS_MS)
        runCurrent()

        assertTrue("ticker on empty buffer must not invoke sink", sink.batches.isEmpty())
        job.cancel()
    }

    @Test
    fun ticker_fires_repeatedly_each_interval() = runTest {
        val sink = RecordingBufferSink()
        val buf = CrashSafeBuffer(sink)
        val job = buf.start(backgroundScope)

        repeat(PARTIAL_BATCH) { i -> buf.enqueue(sample(i.toLong())) }
        advanceTimeBy(FLUSH_INTERVAL_MS)
        runCurrent()
        repeat(PARTIAL_BATCH) { i -> buf.enqueue(sample((PARTIAL_BATCH + i).toLong())) }
        advanceTimeBy(FLUSH_INTERVAL_MS)
        runCurrent()

        assertEquals("two ticker-driven flushes", 2, sink.batches.size)
        assertEquals(PARTIAL_BATCH, sink.batches[0].size)
        assertEquals(PARTIAL_BATCH, sink.batches[1].size)
        job.cancel()
    }

    @Test
    fun batch_preserves_enqueue_order() = runTest {
        val sink = RecordingBufferSink()
        val buf = CrashSafeBuffer(sink)
        repeat(MAX_BATCH_SIZE) { i -> buf.enqueue(sample(SAMPLE_NS_BASE + i)) }

        val batch = sink.batches[0]
        for (i in 0 until MAX_BATCH_SIZE) {
            assertEquals("position $i", SAMPLE_NS_BASE + i, batch[i].tSourceNs)
        }
    }

    private fun sample(ns: Long): TelemetrySample = TelemetrySample(
        tSourceNs = ns,
        tCanonicalNs = ns,
        tSourceClock = SOURCE_CLOCK_DEVICE,
        channels = emptyMap(),
    )
}
