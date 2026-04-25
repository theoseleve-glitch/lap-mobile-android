package com.lap.mobile.data.bundle

import com.lap.schemas.v1.LapRecord
import com.lap.schemas.v1.LapSchemasJson
import com.lap.schemas.v1.UUID7
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val FIXTURE_SESSION_ID: UUID7 = "01997333-9d2e-7000-8000-000000000001"
private const val LAP_1_ID: UUID7 = "01997333-9d2e-7000-8000-000000000010"
private const val LAP_2_ID: UUID7 = "01997333-9d2e-7000-8000-000000000011"

class BundleV2WriterLapsTest {
    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun writeLaps_creates_file_at_laps_path() {
        val writer = newWriter()

        writer.writeLaps(emptyList())

        assertTrue("laps.json must exist", writer.lapsFile.isFile)
    }

    @Test
    fun empty_laps_writes_empty_json_array() {
        val writer = newWriter()

        writer.writeLaps(emptyList())

        assertEquals("[]", writer.lapsFile.readText())
    }

    @Test
    fun laps_round_trip_through_lap_schemas_json() {
        val writer = newWriter()
        val laps = listOf(
            LapRecord(
                id = LAP_1_ID,
                sessionId = FIXTURE_SESSION_ID,
                lapNumber = 1,
                timeMs = 92_345,
                sectorTimesMs = listOf(28_000, 32_000, 32_345),
                valid = true,
            ),
            LapRecord(
                id = LAP_2_ID,
                sessionId = FIXTURE_SESSION_ID,
                lapNumber = 2,
                timeMs = null,
                sectorTimesMs = emptyList(),
                valid = false,
                flags = listOf("off_track"),
            ),
        )

        writer.writeLaps(laps)
        val rehydrated = LapSchemasJson.decodeFromString<List<LapRecord>>(writer.lapsFile.readText())

        assertEquals(laps, rehydrated)
    }

    @Test
    fun laps_use_snake_case_wire_naming() {
        val writer = newWriter()
        val laps = listOf(
            LapRecord(
                id = LAP_1_ID,
                sessionId = FIXTURE_SESSION_ID,
                lapNumber = 7,
                timeMs = 90_000,
                sectorTimesMs = listOf(30_000, 30_000, 30_000),
            ),
        )

        writer.writeLaps(laps)
        val raw = writer.lapsFile.readText()

        assertTrue("expected snake_case session_id, got: $raw", raw.contains("\"session_id\""))
        assertTrue("expected snake_case lap_number, got: $raw", raw.contains("\"lap_number\""))
        assertTrue("expected snake_case sector_times_ms, got: $raw", raw.contains("\"sector_times_ms\""))
    }

    private fun newWriter(): BundleV2Writer {
        val writer = BundleV2Writer(tempRoot = tempFolder.root, sessionId = FIXTURE_SESSION_ID)
        writer.prepare()
        return writer
    }
}
