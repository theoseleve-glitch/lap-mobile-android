package com.lap.mobile.data.bundle

import com.lap.schemas.v1.UUID7
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val FIXTURE_SESSION_ID: UUID7 = "01997333-9d2e-7000-8000-000000000001"

class BundleV2WriterFinalizeTest {
    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun finalizeBundle_renames_tmp_dir_to_final_bundle_dir() {
        val writer = newWriter()
        writer.writeLaps(emptyList())
        writer.writeEvents(emptyList())
        writer.writeChecksums()

        val ok = writer.finalizeBundle()

        assertTrue("finalizeBundle must succeed when temp dir exists", ok)
        assertFalse("temp .tmp dir must NOT remain after finalise", writer.bundleDir.exists())
        assertTrue("final .bundle dir must exist after finalise", writer.finalBundleDir.isDirectory)
    }

    @Test
    fun finalizeBundle_carries_artifacts_into_the_final_dir() {
        val writer = newWriter()
        writer.writeLaps(emptyList())
        writer.writeEvents(emptyList())
        writer.writeChecksums()
        val expectedFiles = setOf("laps.json", "events.json", "checksums.txt")

        writer.finalizeBundle()

        val survivors = writer.finalBundleDir.list()?.toSet() ?: emptySet()
        assertEquals(expectedFiles, survivors)
    }

    @Test
    fun finalizeBundle_refuses_when_final_dir_already_exists_so_existing_bundle_is_not_overwritten() {
        val writer = newWriter()
        writer.writeLaps(emptyList())
        // Pre-existing finalised bundle from a prior session attempt.
        writer.finalBundleDir.mkdirs()
        writer.finalBundleDir.resolve("manifest.json").writeText("existing")

        val ok = writer.finalizeBundle()

        assertFalse("finalizeBundle must NOT overwrite a pre-existing final bundle dir", ok)
        assertEquals("existing", writer.finalBundleDir.resolve("manifest.json").readText())
    }

    @Test
    fun finalizeBundle_returns_false_when_temp_dir_was_never_prepared() {
        val writer = BundleV2Writer(tempRoot = tempFolder.root, sessionId = FIXTURE_SESSION_ID)
        // Skip prepare() — no temp dir to rename.

        val ok = writer.finalizeBundle()

        assertFalse(ok)
    }

    private fun newWriter(): BundleV2Writer {
        val writer = BundleV2Writer(tempRoot = tempFolder.root, sessionId = FIXTURE_SESSION_ID)
        writer.prepare()
        return writer
    }
}
