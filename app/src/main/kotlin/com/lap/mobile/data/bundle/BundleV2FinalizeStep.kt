package com.lap.mobile.data.bundle

import com.lap.schemas.v1.LapRecord
import com.lap.schemas.v1.SessionManifest
import com.lap.schemas.v1.TelemetrySample

/**
 * Synchronously drives the [BundleV2Writer] through the full
 * finalize sequence (T143). Called from the session-stop path
 * (SessionCoordinator) — `run` only returns once the `.bundle`
 * directory exists on disk, so the caller can safely enqueue an
 * upload immediately after `run()` returns true.
 *
 * Order is fixed: prepare → manifest → telemetry → laps → events →
 * checksums → finalize. checksums must come last (it hashes every
 * other artifact) and finalize must come after checksums (it makes
 * the whole dir read-only by virtue of moving it to `.bundle`).
 *
 * Returns `false` when atomic rename fails (e.g., destination
 * exists from a prior session — handled separately by the orphan
 * reconciler) so the caller maps to a finalisation-failure outcome
 * without a thrown exception.
 */
class BundleV2FinalizeStep(private val writer: BundleV2Writer) {
    suspend fun run(
        manifest: SessionManifest,
        samples: List<TelemetrySample>,
        laps: List<LapRecord>,
        events: List<SessionEvent>,
    ): Boolean {
        writer.prepare()
        writer.writeManifest(manifest)
        writer.writeTelemetry(samples)
        writer.writeLaps(laps)
        writer.writeEvents(events)
        writer.writeChecksums()
        return writer.finalizeBundle()
    }
}
