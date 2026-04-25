package com.lap.mobile.data.bundle

import com.lap.schemas.v1.LapSchemasJson
import com.lap.schemas.v1.SessionManifest
import com.lap.schemas.v1.TelemetrySample
import com.lap.schemas.v1.UUID7
import kotlinx.serialization.encodeToString
import java.io.File

private const val SESSION_DIR_PREFIX: String = "session-"
private const val TEMP_DIR_SUFFIX: String = ".tmp"
private const val FINAL_DIR_SUFFIX: String = ".bundle"

/**
 * Writes a v2 capture bundle layout (T131 skeleton). On-disk shape:
 *
 * ```
 * <tempRoot>/session-<id>.tmp/
 *   manifest.json   (T132)
 *   telemetry.csv   (T133, optional .parquet from T134)
 *   laps.json       (T135)
 *   events.json     (T136)
 *   checksums.txt   (T137 — SHA-256 per file)
 * ```
 *
 * The `.tmp` suffix on the in-progress directory means a crash leaves
 * an unmistakably partial artifact for the orphan reconciler
 * ([com.lap.mobile.data.OrphanTempFileReconciler]) to reap. T138's
 * atomic finalise renames `<id>.tmp/` → `<id>.bundle/` only after
 * every artifact + checksum line is durably written, so the upload
 * worker (T146+) only ever sees `*.bundle/` directories — never
 * partial writes.
 *
 * Subsequent tasks fill in the per-artifact writers (T132 manifest,
 * T133/T134 telemetry, T135 laps, T136 events, T137 checksums, T138
 * atomic finalise, T139 sensor-config snapshot, T140 data-quality).
 * T131 is the structural pass — file-path layout + bundle-dir
 * preparation only.
 */
class BundleV2Writer(private val tempRoot: File, private val sessionId: UUID7) {
    val bundleDir: File = File(tempRoot, "$SESSION_DIR_PREFIX$sessionId$TEMP_DIR_SUFFIX")
    val finalBundleDir: File = File(tempRoot, "$SESSION_DIR_PREFIX$sessionId$FINAL_DIR_SUFFIX")
    val manifestFile: File = File(bundleDir, "manifest.json")
    val telemetryFile: File = File(bundleDir, "telemetry.csv")
    val lapsFile: File = File(bundleDir, "laps.json")
    val eventsFile: File = File(bundleDir, "events.json")
    val checksumsFile: File = File(bundleDir, "checksums.txt")

    /**
     * Create the temp bundle directory so per-artifact writers can
     * stream into it. Idempotent — re-preparing an existing bundle
     * dir is a no-op so a session resumed mid-write doesn't error.
     */
    fun prepare() {
        bundleDir.mkdirs()
    }

    /**
     * Write the session manifest JSON (T132). Goes through
     * [LapSchemasJson] so the wire payload is byte-for-byte aligned
     * with the Pydantic source — snake_case keys, encoded defaults,
     * ignore-unknown — matching ADR-012's ingestion contract.
     */
    fun writeManifest(manifest: SessionManifest) {
        manifestFile.writeText(LapSchemasJson.encodeToString(manifest))
    }

    /**
     * Write the per-sample telemetry as CSV-v2 (T133). Both the
     * source-clock timestamp and the canonical-clock timestamp are
     * carried per sample per ADR-007 — the server canonicalises
     * server-side; the client ships both raw so the alignment is
     * auditable.
     *
     * Header layout: `t_source_ns,t_canonical_ns,t_source_clock,
     * <channelA>,<channelB>,...`. Channel names are the sorted union
     * across every sample so the schema is deterministic; a sample
     * missing a channel renders as an empty cell so the server can
     * tell "not measured" from "measured = 0" (ADR-012).
     */
    fun writeTelemetry(samples: List<TelemetrySample>) {
        val channelNames = samples.flatMap { it.channels.keys }.toSortedSet().toList()
        val text = buildString {
            appendCsvHeader(channelNames)
            for (sample in samples) {
                appendCsvRow(sample, channelNames)
            }
        }
        telemetryFile.writeText(text)
    }

    private fun StringBuilder.appendCsvHeader(channelNames: List<String>) {
        append(CSV_FIXED_HEADER)
        for (channel in channelNames) {
            append(',').append(channel)
        }
        append('\n')
    }

    private fun StringBuilder.appendCsvRow(sample: TelemetrySample, channelNames: List<String>) {
        append(sample.tSourceNs.toString())
        append(',').append(sample.tCanonicalNs.toString())
        append(',').append(sample.tSourceClock)
        for (channel in channelNames) {
            append(',')
            val value = sample.channels[channel]
            if (value != null) append(value.toString())
        }
        append('\n')
    }

    companion object {
        private const val CSV_FIXED_HEADER: String = "t_source_ns,t_canonical_ns,t_source_clock"
    }
}
