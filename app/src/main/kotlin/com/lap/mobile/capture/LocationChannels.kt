package com.lap.mobile.capture

/**
 * Channel-name dictionary for [LocationSample] → `TelemetrySample`
 * mapping (T065). The names are wire-stable strings — the server-side
 * ingestion pipeline (ADR-012) looks up these exact keys to decode
 * GNSS-derived telemetry. Renaming any of them requires a coordinated
 * server change.
 *
 * Channels are keyed in a free-form `Map<String, Double>` on
 * `TelemetrySample`; the contract that a given `tSourceClock="gnss"`
 * sample carries `lat`, `lon`, `accuracy_m` (always) and `speed_mps`,
 * `bearing_deg`, `altitude_m` (when present) lives here.
 */
object LocationChannels {
    const val LATITUDE: String = "lat"
    const val LONGITUDE: String = "lon"
    const val ACCURACY_M: String = "accuracy_m"
    const val SPEED_MPS: String = "speed_mps"
    const val BEARING_DEG: String = "bearing_deg"
    const val ALTITUDE_M: String = "altitude_m"
}
