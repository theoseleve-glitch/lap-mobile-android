package com.lap.mobile.data

import com.lap.schemas.v1.TelemetrySample
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Test double for [BufferSink] that records every batch handed off by
 * [CrashSafeBuffer]. `internal` so sibling tests in `data/` can share
 * it without colliding on `private class` declarations (the same
 * pattern as [FixedClock] / [FakeJournalDao]).
 */
internal class RecordingBufferSink : BufferSink {
    private val mutex: Mutex = Mutex()
    private val recorded: MutableList<List<TelemetrySample>> = mutableListOf()

    val batches: List<List<TelemetrySample>>
        get() = recorded.toList()

    override suspend fun writeBatch(batch: List<TelemetrySample>) {
        mutex.withLock { recorded.add(batch) }
    }
}
