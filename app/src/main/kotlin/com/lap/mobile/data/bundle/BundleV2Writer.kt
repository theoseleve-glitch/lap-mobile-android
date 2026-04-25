package com.lap.mobile.data.bundle

import com.lap.schemas.v1.LapRecord
import com.lap.schemas.v1.LapSchemasJson
import com.lap.schemas.v1.SessionManifest
import com.lap.schemas.v1.TelemetrySample
import com.lap.schemas.v1.UUID7
import kotlinx.serialization.encodeToString
import java.io.File
import java.security.MessageDigest

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

    /**
     * Write the lap records as a JSON array (T135). Lap and sector
     * detection happens server-side from raw GPS per ADR-012; the
     * client carries any laps it computed (or none) so the round-trip
     * to the dashboard's offline cache (InsightCacheEntity, T045) is
     * symmetric.
     */
    fun writeLaps(laps: List<LapRecord>) {
        lapsFile.writeText(LapSchemasJson.encodeToString(laps))
    }

    /**
     * Write the session event log as a polymorphic JSON array (T136).
     * See [SessionEvent] for shape — server-side ingestion routes per
     * the `type` discriminator.
     */
    fun writeEvents(events: List<SessionEvent>) {
        eventsFile.writeText(LapSchemasJson.encodeToString(events))
    }

    /**
     * Compute SHA-256 over each existing artifact and write a
     * `<hex>  <filename>` line per file (T137). Format mirrors GNU
     * `sha256sum` so a developer can verify a bundle on the
     * command line. Files that don't exist are intentionally skipped
     * — emitting a checksum line for an absent file would make the
     * server-side integrity check (T144) fail looking for an
     * artifact the bundle never had.
     */
    fun writeChecksums() {
        val artifacts = listOf(manifestFile, telemetryFile, lapsFile, eventsFile)
            .filter { it.isFile }
        val text = buildString {
            for (artifact in artifacts) {
                append(sha256Hex(artifact)).append("  ").append(artifact.name).append('\n')
            }
        }
        checksumsFile.writeText(text)
    }

    /**
     * Atomic rename of the in-progress `.tmp` bundle directory to the
     * finalised `.bundle` directory (T138). On the same filesystem
     * `File.renameTo` is atomic per POSIX rename(2) — either the
     * destination becomes the temp dir's contents wholesale, or
     * nothing changes. This is what makes the upload pipeline
     * (T146+) safe to scan for `*.bundle/` directories without ever
     * seeing a partial write.
     *
     * Refuses to overwrite an existing final dir — a duplicate
     * finalise would otherwise destroy a prior session's artifacts.
     * Returns `false` when the temp dir doesn't exist (prepare not
     * called) or when the destination is occupied; the caller maps
     * either to a finalisation-failure outcome.
     *
     */
    fun finalizeBundle(): Boolean {
        if (!bundleDir.isDirectory) return false
        if (finalBundleDir.exists()) return false
        return bundleDir.renameTo(finalBundleDir)
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance(SHA_256_ALGORITHM)
        file.inputStream().use { stream ->
            val buf = ByteArray(SHA_256_BUF_SIZE)
            while (true) {
                val n = stream.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
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
        private const val SHA_256_ALGORITHM: String = "SHA-256"
        private const val SHA_256_BUF_SIZE: Int = 8_192
    }
}
