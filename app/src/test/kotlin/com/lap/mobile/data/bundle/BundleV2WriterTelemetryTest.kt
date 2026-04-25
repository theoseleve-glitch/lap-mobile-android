package com.lap.mobile.data.bundle

import com.lap.schemas.v1.TelemetrySample
import com.lap.schemas.v1.UUID7
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val FIXTURE_SESSION_ID: UUID7 = "01997333-9d2e-7000-8000-000000000001"
private const val SOURCE_CLOCK_GNSS: String = "gnss"
private const val SOURCE_CLOCK_RACEBOX: String = "racebox_gnss"
private const val EXPECTED_HEADER: String = "t_source_ns,t_canonical_ns,t_source_clock,accuracy_m,lat,lon,speed_mps"
private const val EXPECTED_HEADER_WITH_GAP: String =
    "t_source_ns,t_canonical_ns,t_source_clock,accuracy_m,lat,lon"
private const val EXPECTED_HEADER_EMPTY: String = "t_source_ns,t_canonical_ns,t_source_clock"

class BundleV2WriterTelemetryTest {
    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun writeTelemetry_creates_csv_file_at_telemetry_path() {
        val writer = newWriter()

        writer.writeTelemetry(
            listOf(
                TelemetrySample(
                    tSourceNs = 1_000L,
                    tCanonicalNs = 1_000L,
                    tSourceClock = SOURCE_CLOCK_GNSS,
                    channels = mapOf("lat" to 47.0, "lon" to -122.0, "accuracy_m" to 2.5),
                ),
            ),
        )

        assertTrue("telemetry.csv must exist", writer.telemetryFile.isFile)
        assertTrue("telemetry.csv must not be empty", writer.telemetryFile.length() > 0)
    }

    @Test
    fun header_carries_both_source_and_canonical_ns_per_adr_007() {
        val writer = newWriter()

        writer.writeTelemetry(
            listOf(
                TelemetrySample(
                    tSourceNs = 1_000L,
                    tCanonicalNs = 1_000L,
                    tSourceClock = SOURCE_CLOCK_GNSS,
                    channels = mapOf("lat" to 47.0, "lon" to -122.0, "accuracy_m" to 2.5, "speed_mps" to 10.0),
                ),
            ),
        )

        val firstLine = writer.telemetryFile.readLines().first()
        assertEquals(EXPECTED_HEADER, firstLine)
    }

    @Test
    fun header_unions_channel_names_across_all_samples_in_sorted_order() {
        // Mixed channel sets (e.g., one sample missing speed_mps because the
        // GNSS fix didn't carry it) — header must list every channel any
        // sample has, sorted so the schema is deterministic.
        val writer = newWriter()

        writer.writeTelemetry(
            listOf(
                TelemetrySample(
                    tSourceNs = 1_000L,
                    tCanonicalNs = 1_000L,
                    tSourceClock = SOURCE_CLOCK_GNSS,
                    channels = mapOf("lon" to -122.0, "lat" to 47.0),
                ),
                TelemetrySample(
                    tSourceNs = 2_000L,
                    tCanonicalNs = 2_000L,
                    tSourceClock = SOURCE_CLOCK_GNSS,
                    channels = mapOf("accuracy_m" to 2.5, "lat" to 47.1, "lon" to -122.1),
                ),
            ),
        )

        val firstLine = writer.telemetryFile.readLines().first()
        assertEquals(EXPECTED_HEADER_WITH_GAP, firstLine)
    }

    @Test
    fun rows_carry_timestamps_clock_and_channel_values() {
        val writer = newWriter()

        writer.writeTelemetry(
            listOf(
                TelemetrySample(
                    tSourceNs = 1_700_000_000_000_000_000L,
                    tCanonicalNs = 1_700_000_000_000_000_000L,
                    tSourceClock = SOURCE_CLOCK_RACEBOX,
                    channels = mapOf("lat" to 47.5, "lon" to -122.5),
                ),
            ),
        )

        val rows = writer.telemetryFile.readLines()
        // header + 1 data row
        assertEquals(2, rows.size)
        assertEquals(
            "1700000000000000000,1700000000000000000,racebox_gnss,47.5,-122.5",
            rows[1],
        )
    }

    @Test
    fun missing_channel_renders_as_empty_cell_so_absence_is_distinguishable_from_zero() {
        // Per ADR-012 ingestion: absence ≠ zero. CSV represents absence as
        // an empty cell so the server can tell "not measured" from "measured = 0".
        val writer = newWriter()

        writer.writeTelemetry(
            listOf(
                TelemetrySample(
                    tSourceNs = 1L,
                    tCanonicalNs = 1L,
                    tSourceClock = SOURCE_CLOCK_GNSS,
                    channels = mapOf("lat" to 47.0, "lon" to -122.0),
                ),
                TelemetrySample(
                    tSourceNs = 2L,
                    tCanonicalNs = 2L,
                    tSourceClock = SOURCE_CLOCK_GNSS,
                    channels = mapOf("lat" to 47.1, "lon" to -122.1, "accuracy_m" to 2.5),
                ),
            ),
        )

        val rows = writer.telemetryFile.readLines()
        // First sample has no accuracy_m; column should be empty (trailing comma).
        assertEquals("1,1,gnss,,47.0,-122.0", rows[1])
        assertEquals("2,2,gnss,2.5,47.1,-122.1", rows[2])
    }

    @Test
    fun empty_sample_list_writes_header_only_with_dual_timestamps_and_clock() {
        val writer = newWriter()

        writer.writeTelemetry(emptyList())

        val rows = writer.telemetryFile.readLines()
        assertEquals(1, rows.size)
        assertEquals(EXPECTED_HEADER_EMPTY, rows[0])
    }

    private fun newWriter(): BundleV2Writer {
        val writer = BundleV2Writer(tempRoot = tempFolder.root, sessionId = FIXTURE_SESSION_ID)
        writer.prepare()
        return writer
    }
}
