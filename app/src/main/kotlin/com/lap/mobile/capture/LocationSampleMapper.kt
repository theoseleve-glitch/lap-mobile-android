package com.lap.mobile.capture

import com.lap.schemas.v1.TelemetrySample

/**
 * `tSourceClock` literal for GNSS-derived samples per ADR-007's
 * `SourceClock` vocabulary. Hoisted so a future `t_source_clock`
 * literal change is a single-site edit.
 */
private const val SOURCE_CLOCK_GNSS: String = "gnss"

/**
 * On-device wire-shape mapping: [LocationSample] → [TelemetrySample]
 * (T065). Per ADR-007, mobile clients ship raw sensor timestamps
 * verbatim and set `tCanonicalNs == tSourceNs`; the ingestion
 * pipeline (ADR-012) canonicalizes server-side against
 * `session.started_at`. Pre-fusing on-device would erase the forensic
 * trail.
 *
 * Optional kinematics (speed, bearing, altitude) are absent-by-
 * omission rather than null-encoded — `Map<String, Double>` can't
 * hold null, and absence is what the server uses to distinguish "not
 * measured" from "measured = 0".
 */
fun LocationSample.toTelemetrySample(): TelemetrySample = TelemetrySample(
    tSourceNs = sourceTimestampNs,
    tCanonicalNs = sourceTimestampNs,
    tSourceClock = SOURCE_CLOCK_GNSS,
    channels = buildMap {
        put(LocationChannels.LATITUDE, latitude)
        put(LocationChannels.LONGITUDE, longitude)
        put(LocationChannels.ACCURACY_M, accuracyMeters.toDouble())
        speedMps?.let { put(LocationChannels.SPEED_MPS, it.toDouble()) }
        bearingDeg?.let { put(LocationChannels.BEARING_DEG, it.toDouble()) }
        altitudeMeters?.let { put(LocationChannels.ALTITUDE_M, it) }
    },
)
