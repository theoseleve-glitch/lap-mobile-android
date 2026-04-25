package com.lap.mobile.data.bundle

import com.lap.schemas.v1.DataQuality
import com.lap.schemas.v1.EnvironmentTag
import com.lap.schemas.v1.LapRecord
import com.lap.schemas.v1.LapSchemasJson
import com.lap.schemas.v1.SensorConfig
import com.lap.schemas.v1.SensorConfigSnapshot
import com.lap.schemas.v1.SessionManifest
import com.lap.schemas.v1.SourceTag
import com.lap.schemas.v1.TelemetrySample
import com.lap.schemas.v1.UUID7
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

private const val FIXTURE_SESSION_ID: UUID7 = "01997333-9d2e-7000-8000-000000000001"
private const val FIXTURE_DRIVER_ID: UUID7 = "01997333-9d2e-7000-8000-000000000002"
private const val FIXTURE_CAR_ID: UUID7 = "01997333-9d2e-7000-8000-000000000003"
private const val FIXTURE_TRACK_ID: UUID7 = "01997333-9d2e-7000-8000-000000000004"
private const val FIXTURE_LAP_ID: UUID7 = "01997333-9d2e-7000-8000-000000000010"
private const val SOURCE_CLOCK_RACEBOX: String = "racebox_gnss"
private const val EXPECTED_CSV_LINES: Int = 4 // header + 3 samples
private const val EXPECTED_LINE_PARTS: Int = 2

class BundleV2WriterRoundTripTest {
    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun full_bundle_write_finalize_round_trips_every_artifact() {
        val writer = BundleV2Writer(tempRoot = tempFolder.root, sessionId = FIXTURE_SESSION_ID)
        writer.prepare()
        val manifest = buildManifest()
        val samples = buildSamples()
        val laps = buildLaps()
        val events = listOf<SessionEvent>(
            SessionEvent.SessionStart(tSourceNs = 1_700_000_000_000_000_000L),
            SessionEvent.SessionStop(tSourceNs = 1_700_000_180_000_000_000L),
        )

        writer.writeManifest(manifest)
        writer.writeTelemetry(samples)
        writer.writeLaps(laps)
        writer.writeEvents(events)
        writer.writeChecksums()
        val ok = writer.finalizeBundle()

        assertTrue("finalizeBundle must succeed for a fully-written bundle", ok)
        val finalDir = writer.finalBundleDir

        val rehydratedManifest = LapSchemasJson
            .decodeFromString<SessionManifest>(finalDir.resolve("manifest.json").readText())
        assertEquals(manifest, rehydratedManifest)

        val rehydratedLaps = LapSchemasJson
            .decodeFromString<List<LapRecord>>(finalDir.resolve("laps.json").readText())
        assertEquals(laps, rehydratedLaps)

        val rehydratedEvents = LapSchemasJson
            .decodeFromString<List<SessionEvent>>(finalDir.resolve("events.json").readText())
        assertEquals(events, rehydratedEvents)

        val csvLines = finalDir.resolve("telemetry.csv").readLines().filter { it.isNotBlank() }
        assertEquals(EXPECTED_CSV_LINES, csvLines.size)

        verifyChecksumsMatchArtifacts(finalDir)
    }

    private fun verifyChecksumsMatchArtifacts(finalDir: java.io.File) {
        val checksumsLines = finalDir.resolve("checksums.txt").readLines().filter { it.isNotBlank() }
        for (line in checksumsLines) {
            val parts = line.split("  ", limit = EXPECTED_LINE_PARTS)
            assertEquals(EXPECTED_LINE_PARTS, parts.size)
            val (recordedHex, fileName) = parts
            val artifact = finalDir.resolve(fileName)
            assertTrue("artifact $fileName must exist after round-trip", artifact.isFile)
            val digest = MessageDigest.getInstance("SHA-256")
            val computed = digest.digest(artifact.readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals("checksum mismatch for $fileName after finalize", computed, recordedHex)
        }
    }

    private fun buildManifest(): SessionManifest = SessionManifest(
        sessionId = FIXTURE_SESSION_ID,
        driverId = FIXTURE_DRIVER_ID,
        bundleVersion = "v2",
        captureApp = "lap-mobile-android",
        captureAppVersion = "0.1.0",
        deviceModel = "Pixel 8",
        deviceOs = "android",
        deviceOsVersion = "14",
        source = SourceTag.REAL,
        realHardware = "racebox",
        carId = FIXTURE_CAR_ID,
        trackId = FIXTURE_TRACK_ID,
        trackLayout = "full-course",
        environmentTag = EnvironmentTag.REAL_TRACK_DAY,
        startedAt = "2026-04-25T14:30:00Z",
        endedAt = "2026-04-25T15:15:00Z",
        durationSeconds = 2700.0,
        telemetryChecksums = mapOf(
            "telemetry.csv" to "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        ),
        totalSamples = 3,
        sampleRateHz = 100.0,
        dataQuality = DataQuality(
            score = 92.5,
            channelCompleteness = 99.8,
            sampleGapsCount = 0,
            sampleRateStability = 0.98,
            usableForOverlays = true,
            usableForLongTermCoaching = true,
        ),
        sensorConfig = SensorConfigSnapshot(
            sensors = listOf(
                SensorConfig(name = "gnss", sampleRateHz = 25.0, enabled = true),
                SensorConfig(name = "imu", sampleRateHz = 100.0, enabled = true),
            ),
            hardwareId = "racebox-mini-12345",
            firmwareVersion = "EXT CORE 1.00",
        ),
    )

    private fun buildSamples(): List<TelemetrySample> = listOf(
        TelemetrySample(
            tSourceNs = 1_700_000_000_000_000_000L,
            tCanonicalNs = 1_700_000_000_000_000_000L,
            tSourceClock = SOURCE_CLOCK_RACEBOX,
            channels = mapOf("lat" to 47.0, "lon" to -122.0, "speed_mps" to 0.0),
        ),
        TelemetrySample(
            tSourceNs = 1_700_000_000_040_000_000L,
            tCanonicalNs = 1_700_000_000_040_000_000L,
            tSourceClock = SOURCE_CLOCK_RACEBOX,
            channels = mapOf("lat" to 47.001, "lon" to -122.001, "speed_mps" to 5.0),
        ),
        TelemetrySample(
            tSourceNs = 1_700_000_000_080_000_000L,
            tCanonicalNs = 1_700_000_000_080_000_000L,
            tSourceClock = SOURCE_CLOCK_RACEBOX,
            channels = mapOf("lat" to 47.002, "lon" to -122.002, "speed_mps" to 10.0),
        ),
    )

    private fun buildLaps(): List<LapRecord> = listOf(
        LapRecord(
            id = FIXTURE_LAP_ID,
            sessionId = FIXTURE_SESSION_ID,
            lapNumber = 1,
            timeMs = 92_345,
            sectorTimesMs = listOf(28_000, 32_000, 32_345),
            valid = true,
        ),
    )
}
