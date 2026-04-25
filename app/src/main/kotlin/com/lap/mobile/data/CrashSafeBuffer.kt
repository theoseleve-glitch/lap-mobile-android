package com.lap.mobile.data

import com.lap.schemas.v1.TelemetrySample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration

/**
 * Default size trigger — flush whenever this many samples are queued.
 * 500 ≈ 10 s of merged location + motion (~50 Hz combined) so the size
 * trigger and the time trigger meet at roughly the same cadence under
 * normal conditions; either path covers the other when the cadence
 * varies (idle pauses fall back on the timer; high-frequency bursts on
 * the size threshold).
 */
internal const val DEFAULT_MAX_BATCH_SIZE: Int = 500

/**
 * Default time trigger — periodic ticker fires this often. Crash-safety
 * bound: the worst-case sample loss on an OS kill is ~[DEFAULT_MAX_BATCH_SIZE]
 * samples *or* this many seconds, whichever comes first.
 */
internal const val DEFAULT_FLUSH_INTERVAL_SECONDS: Long = 10L

/**
 * Crash-safe in-memory buffer (T079). Samples accumulate in [pending]
 * and flush to [sink] on either of two triggers — the only thing that
 * matters for crash-safety is *bounded loss*:
 *
 *  - **size trigger** ([maxBatchSize]) — fires inside [enqueue] when
 *    the buffer reaches the threshold; covers high-frequency bursts.
 *  - **time trigger** ([flushInterval]) — fires from the periodic
 *    coroutine started by [start]; covers idle gaps where samples
 *    arrive too slowly to ever hit the size threshold.
 *
 * Both paths take the [Mutex], so the size-flush in [enqueue] and the
 * timer-flush in [start] never overlap. The flushed batch is a snapshot
 * (`pending.toList()`) — the buffer keeps accepting new samples while
 * the sink writes, so a slow disk does not pause capture.
 *
 * No injected `Clock`: the timer uses [delay], which `runTest` virtual
 * time already advances, and the buffer doesn't otherwise care about
 * wall-clock instants. Sample timestamps are carried by
 * [TelemetrySample] itself.
 *
 * The buffer does NOT own its [CoroutineScope] — the foreground service
 * (T063) passes one in via [start] so the ticker dies with the
 * recording lifecycle.
 */
class CrashSafeBuffer(
    private val sink: BufferSink,
    private val maxBatchSize: Int = DEFAULT_MAX_BATCH_SIZE,
    private val flushInterval: Duration = Duration.ofSeconds(DEFAULT_FLUSH_INTERVAL_SECONDS),
) {
    private val mutex: Mutex = Mutex()
    private val pending: MutableList<TelemetrySample> = mutableListOf()

    suspend fun enqueue(sample: TelemetrySample) {
        val toFlush: List<TelemetrySample>? = mutex.withLock {
            pending.add(sample)
            if (pending.size >= maxBatchSize) drainLocked() else null
        }
        toFlush?.let { sink.writeBatch(it) }
    }

    suspend fun flushNow() {
        val toFlush: List<TelemetrySample>? = mutex.withLock { drainLocked() }
        toFlush?.let { sink.writeBatch(it) }
    }

    fun start(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            delay(flushInterval.toMillis())
            flushNow()
        }
    }

    private fun drainLocked(): List<TelemetrySample>? {
        if (pending.isEmpty()) return null
        val batch = pending.toList()
        pending.clear()
        return batch
    }
}
