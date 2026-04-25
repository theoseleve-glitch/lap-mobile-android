package com.lap.mobile.data.bundle

import com.lap.schemas.v1.DataQuality
import com.lap.schemas.v1.EnvironmentTag
import com.lap.schemas.v1.SensorConfigSnapshot
import com.lap.schemas.v1.SessionManifest
import com.lap.schemas.v1.SourceTag
import com.lap.schemas.v1.TelemetrySample
import com.lap.schemas.v1.UUID7
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val FIXTURE_SESSION_ID: UUID7 = "01997333-9d2e-7000-8000-000000000001"
private const val FIXTURE_DRIVER_ID: UUID7 = "01997333-9d2e-7000-8000-000000000002"
private const val FIXTURE_CAR_ID: UUID7 = "01997333-9d2e-7000-8000-000000000003"
private const val FIXTURE_TRACK_ID: UUID7 = "01997333-9d2e-7000-8000-000000000004"
private const val SOURCE_CLOCK_GNSS: String = "gnss"
private val MIN_MANIFEST: SessionManifest = SessionManifest(
    sessionId = FIXTURE_SESSION_ID,
    driverId = FIXTURE_DRIVER_ID,
    bundleVersion = "v2",
    captureApp = "lap-mobile-android",
    captureAppVersion = "0.1.0",
    deviceModel = "Pixel 8",
    deviceOs = "android",
    deviceOsVersion = "14",
    source = SourceTag.REAL,
    carId = FIXTURE_CAR_ID,
    trackId = FIXTURE_TRACK_ID,
    trackLayout = "full-course",
    environmentTag = EnvironmentTag.REAL_TRACK_DAY,
    startedAt = "2026-04-25T14:30:00Z",
    endedAt = "2026-04-25T15:15:00Z",
    durationSeconds = 2700.0,
    telemetryChecksums = emptyMap(),
    totalSamples = 1,
    sampleRateHz = 100.0,
    dataQuality = DataQuality(
        score = 100.0,
        channelCompleteness = 100.0,
        sampleGapsCount = 0,
        sampleRateStability = 1.0,
        usableForOverlays = true,
        usableForLongTermCoaching = true,
    ),
    sensorConfig = SensorConfigSnapshot(),
)

class BundleV2FinalizeStepTest {
    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun run_returns_true_only_after_final_bundle_dir_exists_on_disk() = runTest {
        val writer = BundleV2Writer(tempRoot = tempFolder.root, sessionId = FIXTURE_SESSION_ID)
        val finalizer = BundleV2FinalizeStep(writer)

        val ok = finalizer.run(MIN_MANIFEST, sampleSamples(), emptyList(), emptyList())

        assertTrue("run must report success", ok)
        // The "blocks until complete" guarantee — when run returns true,
        // the .bundle dir must already exist in the filesystem; no
        // background work is allowed to happen after the return.
        assertTrue(
            "final .bundle dir must exist immediately after run() returns",
            writer.finalBundleDir.isDirectory,
        )
        assertFalse(
            "temp .tmp dir must NOT remain after run() returns",
            writer.bundleDir.exists(),
        )
    }

    @Test
    fun run_writes_every_artifact_before_returning() = runTest {
        val writer = BundleV2Writer(tempRoot = tempFolder.root, sessionId = FIXTURE_SESSION_ID)
        val finalizer = BundleV2FinalizeStep(writer)

        finalizer.run(MIN_MANIFEST, sampleSamples(), emptyList(), emptyList())

        val survivors = writer.finalBundleDir.list()?.toSet() ?: emptySet()
        assertEquals(
            setOf("manifest.json", "telemetry.csv", "laps.json", "events.json", "checksums.txt"),
            survivors,
        )
    }

    @Test
    fun run_returns_false_when_atomic_rename_fails() = runTest {
        // Pre-existing finalised bundle from a prior session attempt.
        val writer = BundleV2Writer(tempRoot = tempFolder.root, sessionId = FIXTURE_SESSION_ID)
        writer.finalBundleDir.mkdirs()

        val finalizer = BundleV2FinalizeStep(writer)
        val ok = finalizer.run(MIN_MANIFEST, sampleSamples(), emptyList(), emptyList())

        assertFalse("run must report failure when finalize cannot atomically swap", ok)
    }

    private fun sampleSamples(): List<TelemetrySample> = listOf(
        TelemetrySample(
            tSourceNs = 1_000L,
            tCanonicalNs = 1_000L,
            tSourceClock = SOURCE_CLOCK_GNSS,
            channels = mapOf("lat" to 47.0, "lon" to -122.0),
        ),
    )
}
