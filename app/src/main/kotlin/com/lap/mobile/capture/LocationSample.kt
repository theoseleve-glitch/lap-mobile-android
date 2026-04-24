package com.lap.mobile.capture

/**
 * Raw location sample as captured from the device's GNSS subsystem.
 * The mapping to the wire-shape `TelemetrySample` (with
 * `t_source_clock="gnss"` per ADR-007) happens in T065 — this type
 * is just what flows out of [LocationCaptureService].
 *
 * Two timestamps are carried per ADR-007:
 *  - [sourceTimestampNs]: the raw device monotonic clock at fix time
 *    (`Location.elapsedRealtimeNanos`). Stable against NITZ wall-clock
 *    re-sync, which is what server-side canonical alignment needs.
 *  - [receiptUtcMillis]: wall-clock UTC ms (`Location.time`). Anchors
 *    the canonical timeline against the session's `started_at`.
 *
 * Mobile clients ship both raw — no on-device fusion. Canonical
 * alignment runs server-side per ADR-007.
 */
data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val altitudeMeters: Double?,
    val speedMps: Float?,
    val bearingDeg: Float?,
    val sourceTimestampNs: Long,
    val receiptUtcMillis: Long,
)
