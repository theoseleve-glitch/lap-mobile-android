package com.lap.mobile.data.bundle

import com.lap.schemas.v1.LapSchemasJson
import com.lap.schemas.v1.UUID7
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val FIXTURE_SESSION_ID: UUID7 = "01997333-9d2e-7000-8000-000000000001"
private const val SESSION_START_NS: Long = 1_700_000_000_000_000_000L
private const val LAP_1_NS: Long = 1_700_000_092_345_000_000L
private const val FLAG_NS: Long = 1_700_000_120_000_000_000L
private const val SESSION_STOP_NS: Long = 1_700_000_180_000_000_000L

class BundleV2WriterEventsTest {
    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun writeEvents_creates_file_at_events_path() {
        val writer = newWriter()

        writer.writeEvents(emptyList())

        assertTrue("events.json must exist", writer.eventsFile.isFile)
    }

    @Test
    fun empty_events_writes_empty_json_array() {
        val writer = newWriter()

        writer.writeEvents(emptyList())

        assertEquals("[]", writer.eventsFile.readText())
    }

    @Test
    fun events_round_trip_through_lap_schemas_json_polymorphism() {
        val writer = newWriter()
        val events: List<SessionEvent> = listOf(
            SessionEvent.SessionStart(tSourceNs = SESSION_START_NS),
            SessionEvent.LapCross(tSourceNs = LAP_1_NS, lapNumber = 1),
            SessionEvent.Flag(tSourceNs = FLAG_NS, kind = "yellow"),
            SessionEvent.SessionStop(tSourceNs = SESSION_STOP_NS),
        )

        writer.writeEvents(events)
        val rehydrated = LapSchemasJson.decodeFromString<List<SessionEvent>>(writer.eventsFile.readText())

        assertEquals(events, rehydrated)
    }

    @Test
    fun events_carry_type_discriminator_so_server_can_route_per_subtype() {
        val writer = newWriter()
        val events: List<SessionEvent> = listOf(
            SessionEvent.LapCross(tSourceNs = LAP_1_NS, lapNumber = 7),
        )

        writer.writeEvents(events)
        val raw = writer.eventsFile.readText()

        // kotlinx polymorphism uses the "type" field by default + the SerialName.
        assertTrue("expected type discriminator, got: $raw", raw.contains("\"type\":\"lap_cross\""))
    }

    @Test
    fun events_use_snake_case_for_payload_fields() {
        val writer = newWriter()
        val events: List<SessionEvent> = listOf(
            SessionEvent.LapCross(tSourceNs = LAP_1_NS, lapNumber = 3),
        )

        writer.writeEvents(events)
        val raw = writer.eventsFile.readText()

        assertTrue("expected snake_case t_source_ns, got: $raw", raw.contains("\"t_source_ns\""))
        assertTrue("expected snake_case lap_number, got: $raw", raw.contains("\"lap_number\""))
    }

    private fun newWriter(): BundleV2Writer {
        val writer = BundleV2Writer(tempRoot = tempFolder.root, sessionId = FIXTURE_SESSION_ID)
        writer.prepare()
        return writer
    }
}
