package com.lap.mobile.data

import com.lap.mobile.core.DomainError
import com.lap.mobile.core.Result
import java.time.Instant

/**
 * Writes a crashed session's buffer file into a finalized bundle on
 * disk. The real implementation lands with T131 ([BundleV2Writer]);
 * T059 treats it as an abstract collaborator so [FinalizeFromJournal]
 * is testable without touching disk or kotlinx-serialization.
 *
 * The finalizer reads the buffer up to [SessionJournalEntity.fileOffset]
 * (truncating anything past that — a crash can leave a partial write
 * tail), recomputes checksums, and emits the manifest + telemetry per
 * ADR-012.
 */
interface BundleFinalizer {
    data class FinalizedBundle(
        val endedAt: Instant,
        val fileUri: String,
        val sampleCount: Int,
    )

    suspend fun finalize(session: SessionEntity, journal: SessionJournalEntity): Result<FinalizedBundle, DomainError>
}
