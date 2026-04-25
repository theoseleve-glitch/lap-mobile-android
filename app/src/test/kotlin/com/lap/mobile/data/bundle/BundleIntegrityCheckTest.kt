package com.lap.mobile.data.bundle

import com.lap.schemas.v1.UUID7
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val FIXTURE_SESSION_ID: UUID7 = "01997333-9d2e-7000-8000-000000000001"

class BundleIntegrityCheckTest {
    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun valid_finalized_bundle_passes() {
        val finalDir = buildValidBundle()

        val result = BundleIntegrityCheck.verify(finalDir)

        assertEquals(BundleIntegrityResult.Ok, result)
    }

    @Test
    fun missing_checksums_file_fails_fast_before_per_artifact_checks() {
        val finalDir = buildValidBundle()
        finalDir.resolve("checksums.txt").delete()

        val result = BundleIntegrityCheck.verify(finalDir)

        assertEquals(
            BundleIntegrityResult.Failed(listOf(BundleIntegrityCheck.REASON_MISSING_CHECKSUMS)),
            result,
        )
    }

    @Test
    fun missing_artifact_named_in_checksums_surfaces_one_failure_per_missing_file() {
        val finalDir = buildValidBundle()
        finalDir.resolve("laps.json").delete()

        val result = BundleIntegrityCheck.verify(finalDir)

        assertTrue("expected Failed with missing_file:laps.json, got $result", result is BundleIntegrityResult.Failed)
        val failed = result as BundleIntegrityResult.Failed
        assertTrue(
            "reasons must mention laps.json missing, got ${failed.reasons}",
            failed.reasons.any { it == "${BundleIntegrityCheck.REASON_MISSING_FILE}:laps.json" },
        )
    }

    @Test
    fun empty_artifact_fails_with_empty_file_reason() {
        val finalDir = buildValidBundle()
        finalDir.resolve("events.json").writeText("")

        val result = BundleIntegrityCheck.verify(finalDir)

        val failed = result as BundleIntegrityResult.Failed
        assertTrue(
            "expected empty_file:events.json in $failed",
            failed.reasons.any { it == "${BundleIntegrityCheck.REASON_EMPTY_FILE}:events.json" },
        )
    }

    @Test
    fun corrupted_artifact_fails_with_checksum_mismatch_reason() {
        val finalDir = buildValidBundle()
        // Corrupt the file but keep its non-empty length.
        finalDir.resolve("laps.json").writeText("[\"corrupted\"]")

        val result = BundleIntegrityCheck.verify(finalDir)

        val failed = result as BundleIntegrityResult.Failed
        assertTrue(
            "expected checksum_mismatch:laps.json in $failed",
            failed.reasons.any { it == "${BundleIntegrityCheck.REASON_CHECKSUM_MISMATCH}:laps.json" },
        )
    }

    private fun buildValidBundle(): java.io.File {
        val writer = BundleV2Writer(tempRoot = tempFolder.root, sessionId = FIXTURE_SESSION_ID)
        writer.prepare()
        writer.writeLaps(emptyList())
        writer.writeEvents(emptyList())
        writer.writeChecksums()
        check(writer.finalizeBundle()) { "test setup must finalize a valid bundle" }
        return writer.finalBundleDir
    }
}
