package com.lap.mobile.data

import com.lap.schemas.v1.TelemetrySample

/**
 * Pluggable target for [CrashSafeBuffer] flushes (T079). Decouples the
 * buffer's flush triggers (size / time) from the on-disk wire format,
 * which T080 nails down (newline-delimited JSON vs length-prefixed
 * binary). Tests pass a recording fake; the foreground service wires a
 * file-backed implementation in T080.
 *
 * Implementations must be safe to invoke from a coroutine and may
 * suspend (real disk I/O typically dispatches to `Dispatchers.IO`). The
 * batch is the snapshot the buffer hands off — implementers must not
 * mutate it.
 */
fun interface BufferSink {
    suspend fun writeBatch(batch: List<TelemetrySample>)
}
