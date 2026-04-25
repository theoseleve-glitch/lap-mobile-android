package com.lap.mobile.data.bundle

import com.lap.schemas.v1.UUID7
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val FIXTURE_SESSION_ID: UUID7 = "01997333-9d2e-7000-8000-000000000001"

class BundleV2WriterTest {
    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun bundle_dir_is_session_scoped_inside_the_provided_temp_root() {
        val writer = BundleV2Writer(tempRoot = tempFolder.root, sessionId = FIXTURE_SESSION_ID)

        assertTrue(
            "expected bundle dir to start with session id, was ${writer.bundleDir.name}",
            writer.bundleDir.name.startsWith("session-$FIXTURE_SESSION_ID"),
        )
        assertEquals(tempFolder.root, writer.bundleDir.parentFile)
    }

    @Test
    fun bundle_dir_carries_the_dot_tmp_suffix_so_atomic_rename_distinguishes_in_progress_writes() {
        // Atomic finalize (T138) renames bundleDir → finalDir; the .tmp
        // suffix on the in-progress dir means a crash leaves an unmistakably
        // partial artifact for the orphan reconciler to reap.
        val writer = BundleV2Writer(tempRoot = tempFolder.root, sessionId = FIXTURE_SESSION_ID)

        assertTrue(writer.bundleDir.name.endsWith(".tmp"))
        assertTrue(writer.finalBundleDir.name.endsWith(".bundle"))
    }

    @Test
    fun final_bundle_dir_differs_from_temp_bundle_dir() {
        val writer = BundleV2Writer(tempRoot = tempFolder.root, sessionId = FIXTURE_SESSION_ID)

        assertNotEquals(writer.bundleDir, writer.finalBundleDir)
    }

    @Test
    fun bundle_layout_locates_each_artifact_inside_bundle_dir() {
        val writer = BundleV2Writer(tempRoot = tempFolder.root, sessionId = FIXTURE_SESSION_ID)

        assertEquals(writer.bundleDir, writer.manifestFile.parentFile)
        assertEquals(writer.bundleDir, writer.telemetryFile.parentFile)
        assertEquals(writer.bundleDir, writer.lapsFile.parentFile)
        assertEquals(writer.bundleDir, writer.eventsFile.parentFile)
        assertEquals(writer.bundleDir, writer.checksumsFile.parentFile)
    }

    @Test
    fun artifact_filenames_match_the_v2_bundle_layout_contract() {
        val writer = BundleV2Writer(tempRoot = tempFolder.root, sessionId = FIXTURE_SESSION_ID)

        assertEquals("manifest.json", writer.manifestFile.name)
        assertEquals("telemetry.csv", writer.telemetryFile.name)
        assertEquals("laps.json", writer.lapsFile.name)
        assertEquals("events.json", writer.eventsFile.name)
        assertEquals("checksums.txt", writer.checksumsFile.name)
    }

    @Test
    fun prepare_creates_the_temp_bundle_directory() {
        val writer = BundleV2Writer(tempRoot = tempFolder.root, sessionId = FIXTURE_SESSION_ID)

        writer.prepare()

        assertTrue("temp bundle dir must exist after prepare()", writer.bundleDir.isDirectory)
    }
}
