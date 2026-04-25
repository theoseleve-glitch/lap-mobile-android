package com.lap.mobile.capture

internal const val DEFAULT_MIN_FREE_STORAGE_BYTES: Long = 500L * 1024L * 1024L

/**
 * Preflight check that the recording target volume has enough free
 * space (T093). Threshold is [DEFAULT_MIN_FREE_STORAGE_BYTES] (500
 * MB) — at the expected raw bundle rate of ~250 MB/h, that's two
 * hours of headroom before [com.lap.mobile.data.NdjsonBufferSink]
 * would start failing on append.
 *
 * The bound is constructor-injected so a future per-bundle-size
 * tuning (or a "double the headroom for endurance sessions" config)
 * is one Hilt binding away. Boundary semantics: `>= minFreeBytes`
 * passes, so exactly 500 MB is acceptable — the user shouldn't have
 * to free a single extra byte to satisfy an arbitrary strict-greater
 * boundary.
 */
class StorageAvailabilityCheck(
    private val status: StorageStatus,
    private val minFreeBytes: Long = DEFAULT_MIN_FREE_STORAGE_BYTES,
) : PreflightCheck {
    override suspend fun failureReason(): String? {
        return if (status.availableBytes() >= minFreeBytes) null else REASON_STORAGE_LOW
    }

    companion object {
        const val REASON_STORAGE_LOW: String = "storage_low"
    }
}
