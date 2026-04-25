package com.lap.mobile.capture.ble

import com.lap.mobile.capture.LocationChannels
import com.lap.schemas.v1.TelemetrySample
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * `tSourceClock` literal for RaceBox-derived samples per ADR-007's
 * `SourceClock` vocabulary. Distinct from `"gnss"` so the server can
 * tell phone-GPS samples apart from RaceBox-GPS samples — both carry
 * lat/lon but their clocks drift differently.
 */
private const val SOURCE_CLOCK_RACEBOX_GNSS: String = "racebox_gnss"

private const val NS_PER_MS: Long = 1_000_000L
private const val MM_PER_METRE: Double = 1_000.0

/**
 * On-device wire-shape mapping: parsed [NavPvt] → [TelemetrySample]
 * (T117). Per ADR-007 mobile clients ship raw timestamps verbatim
 * with `tCanonicalNs == tSourceNs`; the ingestion pipeline (ADR-012)
 * canonicalises server-side against `session.started_at`. Pre-fusing
 * on-device would erase the forensic trail.
 *
 * The source clock value is derived from NAV-PVT's UTC date fields
 * (year/month/day/hour/min/sec) rather than the phone's elapsed-
 * realtime when the BLE packet arrives — those two clocks drift, and
 * the GPS-derived stamp is the one the server can align against the
 * phone-GNSS stream.
 *
 * Channel names re-use [LocationChannels] because the wire contract
 * is keyed by *what the data is* (lat/lon/etc.) not *which device
 * produced it*. Distance fields convert mm → metres and velocity mm/s
 * → m/s; heading is already in degrees from the parser. Altitude is
 * MSL-relative (heightMslMm) to match the phone-GNSS convention.
 */
fun NavPvt.toTelemetrySample(): TelemetrySample {
    val epochMillis = LocalDateTime
        .of(year, month, day, hour, minute, second)
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli()
    val sourceNs = epochMillis * NS_PER_MS
    return TelemetrySample(
        tSourceNs = sourceNs,
        tCanonicalNs = sourceNs,
        tSourceClock = SOURCE_CLOCK_RACEBOX_GNSS,
        channels = mapOf(
            LocationChannels.LATITUDE to latitudeDeg,
            LocationChannels.LONGITUDE to longitudeDeg,
            LocationChannels.ACCURACY_M to horizontalAccuracyMm / MM_PER_METRE,
            LocationChannels.SPEED_MPS to groundSpeedMmPerSec / MM_PER_METRE,
            LocationChannels.BEARING_DEG to headingDeg,
            LocationChannels.ALTITUDE_M to heightMslMm / MM_PER_METRE,
        ),
    )
}
