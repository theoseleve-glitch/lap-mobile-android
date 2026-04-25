package com.lap.mobile.capture

/**
 * Free-bytes reading on the recording target volume for
 * [StorageAvailabilityCheck] (T093). The Android impl wraps
 * `File(path).usableSpace` (or `StatFs`) on the same directory
 * [com.lap.mobile.data.NdjsonBufferSink] writes to, so the check
 * answers exactly the question that matters: "can I keep buffering
 * here?".
 */
fun interface StorageStatus {
    suspend fun availableBytes(): Long
}
