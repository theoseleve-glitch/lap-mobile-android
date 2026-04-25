package com.lap.mobile.data.bundle

import com.lap.schemas.v1.TelemetrySample
import com.lap.schemas.v1.UUID7
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

private const val FIXTURE_SESSION_ID: UUID7 = "01997333-9d2e-7000-8000-000000000001"
private const val SOURCE_CLOCK_GNSS: String = "gnss"
private const val EXPECTED_LINE_PARTS: Int = 2

class BundleV2WriterRetryTest {
    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun incomplete_write_then_full_retry_produces_correct_bundle_no_stale_artifacts() {
        // Attempt 1: writes some content, then "dies" before finalize.
        val firstAttempt = newWriter()
        firstAttempt.writeTelemetry(buildSamples(staleValues = true))
        firstAttempt.writeLaps(emptyList())
        // Simulate process death — no writeChecksums, no finalizeBundle.

        // Attempt 2: fresh writer for same session, full success path.
        val retry = newWriter()
        retry.writeTelemetry(buildSamples(staleValues = false))
        retry.writeLaps(emptyList())
        retry.writeEvents(emptyList())
        retry.writeChecksums()
        val ok = retry.finalizeBundle()

        // Final bundle reflects the RETRY content, not stale attempt-1 data.
        assertTrue("retry must finalize cleanly when prior attempt left only a .tmp", ok)
        assertTrue(retry.finalBundleDir.isDirectory)

        val csvText = retry.finalBundleDir.resolve("telemetry.csv").readText()
        assertTrue(
            "telemetry must carry RETRY values (47.5/-122.5), not stale (47.0/-122.0). got: $csvText",
            csvText.contains("47.5") && csvText.contains("-122.5"),
        )
        assertFalse(
            "telemetry must NOT carry stale attempt-1 values. got: $csvText",
            csvText.contains("47.0") && csvText.contains("-122.0"),
        )
        verifyAllChecksumsValid(retry.finalBundleDir)
    }

    @Test
    fun retry_after_finalize_refuses_to_clobber_finalized_bundle() {
        // Attempt 1 succeeded — final .bundle dir exists.
        val firstAttempt = newWriter()
        firstAttempt.writeChecksums()
        assertTrue(firstAttempt.finalizeBundle())

        // Attempt 2: same session id; finalizeBundle must refuse to overwrite.
        val retry = newWriter()
        retry.writeTelemetry(buildSamples(staleValues = false))
        retry.writeChecksums()
        val ok = retry.finalizeBundle()

        assertFalse(
            "retry must NOT clobber a previously-finalized bundle — uploads may already be in flight",
            ok,
        )
    }

    private fun newWriter(): BundleV2Writer {
        val writer = BundleV2Writer(tempRoot = tempFolder.root, sessionId = FIXTURE_SESSION_ID)
        writer.prepare()
        return writer
    }

    private fun buildSamples(staleValues: Boolean): List<TelemetrySample> {
        val lat = if (staleValues) 47.0 else 47.5
        val lon = if (staleValues) -122.0 else -122.5
        return listOf(
            TelemetrySample(
                tSourceNs = 1_000L,
                tCanonicalNs = 1_000L,
                tSourceClock = SOURCE_CLOCK_GNSS,
                channels = mapOf("lat" to lat, "lon" to lon),
            ),
        )
    }

    private fun verifyAllChecksumsValid(finalDir: java.io.File) {
        val checksumsLines = finalDir.resolve("checksums.txt").readLines().filter { it.isNotBlank() }
        for (line in checksumsLines) {
            val parts = line.split("  ", limit = EXPECTED_LINE_PARTS)
            val (recordedHex, fileName) = parts
            val artifact = finalDir.resolve(fileName)
            assertTrue("$fileName must exist after retry", artifact.isFile)
            val computed = MessageDigest.getInstance("SHA-256")
                .digest(artifact.readBytes())
                .joinToString("") { "%02x".format(it) }
            assertEquals("retry bundle: checksum mismatch on $fileName", computed, recordedHex)
        }
        assertTrue("post-finalize verification: must have at least one artifact", checksumsLines.isNotEmpty())
    }
}
