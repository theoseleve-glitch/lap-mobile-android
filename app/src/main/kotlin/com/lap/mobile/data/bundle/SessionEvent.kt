package com.lap.mobile.data.bundle

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Bundle-level session event log entry (T136). Sealed polymorphism
 * over four event kinds — start, lap cross, flag, stop — with a
 * monotonic source-clock timestamp ([tSourceNs]) per event so the
 * server can correlate the event stream against the per-sample
 * telemetry rows on the same clock.
 *
 * Serialised through [com.lap.schemas.v1.LapSchemasJson] which uses
 * the default kotlinx-serialization "type" discriminator + the
 * `@SerialName` literal on each subclass — server-side ingestion
 * routes per type. Snake_case wire keys come from
 * `JsonNamingStrategy.SnakeCase` on `LapSchemasJson`.
 *
 * Lap and sector detection still happen server-side from raw GPS
 * (ADR-012); a [LapCross] event here is a *client-detected* hint
 * (e.g., from a course-cross beacon or a manual marker) — the
 * server's authoritative laps come from the lap detector, which
 * may or may not agree.
 */
@Serializable
sealed interface SessionEvent {
    val tSourceNs: Long

    @Serializable
    @SerialName("session_start")
    data class SessionStart(override val tSourceNs: Long) : SessionEvent

    @Serializable
    @SerialName("lap_cross")
    data class LapCross(override val tSourceNs: Long, val lapNumber: Int) : SessionEvent

    @Serializable
    @SerialName("flag")
    data class Flag(override val tSourceNs: Long, val kind: String) : SessionEvent

    @Serializable
    @SerialName("session_stop")
    data class SessionStop(override val tSourceNs: Long) : SessionEvent
}
