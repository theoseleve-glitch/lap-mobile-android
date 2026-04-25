package com.lap.mobile.data.bundle

import com.lap.schemas.v1.DataQuality
import com.lap.schemas.v1.EnvironmentTag
import com.lap.schemas.v1.LapSchemasJson
import com.lap.schemas.v1.SensorConfig
import com.lap.schemas.v1.SensorConfigSnapshot
import com.lap.schemas.v1.SessionManifest
import com.lap.schemas.v1.SourceTag
import com.lap.schemas.v1.UUID7
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val FIXTURE_SESSION_ID: UUID7 = "01997333-9d2e-7000-8000-000000000001"
private const val FIXTURE_DRIVER_ID: UUID7 = "01997333-9d2e-7000-8000-000000000002"
private const val FIXTURE_CAR_ID: UUID7 = "01997333-9d2e-7000-8000-000000000003"
private const val FIXTURE_TRACK_ID: UUID7 = "01997333-9d2e-7000-8000-000000000004"

private val FIXTURE_MANIFEST: SessionManifest = SessionManifest(
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
    totalSamples = 270_000,
    sampleRateHz = 100.0,
    dataQuality = DataQuality(
        score = 92.5,
        channelCompleteness = 99.8,
        sampleGapsCount = 2,
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

class BundleV2WriterManifestTest {
    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun writeManifest_creates_file_at_manifest_path() {
        val writer = BundleV2Writer(tempRoot = tempFolder.root, sessionId = FIXTURE_SESSION_ID)
        writer.prepare()

        writer.writeManifest(FIXTURE_MANIFEST)

        assertTrue("manifest.json must exist after writeManifest", writer.manifestFile.isFile)
        assertTrue("manifest.json must not be empty", writer.manifestFile.length() > 0)
    }

    @Test
    fun writeManifest_round_trips_through_lap_schemas_json() {
        val writer = BundleV2Writer(tempRoot = tempFolder.root, sessionId = FIXTURE_SESSION_ID)
        writer.prepare()

        writer.writeManifest(FIXTURE_MANIFEST)
        val rehydrated = LapSchemasJson.decodeFromString<SessionManifest>(writer.manifestFile.readText())

        assertEquals(FIXTURE_MANIFEST, rehydrated)
    }

    @Test
    fun manifest_uses_snake_case_wire_naming_per_lap_schemas_json() {
        val writer = BundleV2Writer(tempRoot = tempFolder.root, sessionId = FIXTURE_SESSION_ID)
        writer.prepare()

        writer.writeManifest(FIXTURE_MANIFEST)
        val raw = writer.manifestFile.readText()

        // Pydantic source uses snake_case; LapSchemasJson maps Kotlin
        // camelCase → snake_case via JsonNamingStrategy.SnakeCase.
        assertTrue("expected snake_case session_id, got: $raw", raw.contains("\"session_id\""))
        assertTrue("expected snake_case capture_app, got: $raw", raw.contains("\"capture_app\""))
        assertTrue("expected snake_case sample_rate_hz, got: $raw", raw.contains("\"sample_rate_hz\""))
    }
}
