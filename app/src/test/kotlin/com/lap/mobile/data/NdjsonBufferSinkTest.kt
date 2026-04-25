package com.lap.mobile.data

import com.lap.schemas.v1.TelemetrySample
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private const val SOURCE_CLOCK_DEVICE: String = "device_monotonic"
private const val SOURCE_CLOCK_GNSS: String = "gnss"
private const val SAMPLE_NS_BASE: Long = 1_000L
private const val FIRST_BATCH_SIZE: Int = 3
private const val SECOND_BATCH_SIZE: Int = 2
private const val TOTAL_AFTER_TWO_BATCHES: Int = FIRST_BATCH_SIZE + SECOND_BATCH_SIZE

/**
 * Unit tests for [NdjsonBufferSink] (T080). Verifies the on-disk
 * format contract — one JSON-encoded [TelemetrySample] per line, LF
 * line terminator, append semantics across successive batches, and
 * round-trip decode via the same `Json` instance.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NdjsonBufferSinkTest {
    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun write_batch_appends_one_json_line_per_sample() = runTest {
        val file = tempFolder.newFile("samples.ndjson")
        val sink = NdjsonBufferSink(file, ioDispatcher = UnconfinedTestDispatcher(testScheduler))
        val batch = (0 until FIRST_BATCH_SIZE).map { i -> sample(SAMPLE_NS_BASE + i) }

        sink.writeBatch(batch)

        val lines = file.readText(Charsets.UTF_8).split('\n').filter { it.isNotEmpty() }
        assertEquals(FIRST_BATCH_SIZE, lines.size)
    }

    @Test
    fun successive_batches_append_to_same_file() = runTest {
        val file = tempFolder.newFile("samples.ndjson")
        val sink = NdjsonBufferSink(file, ioDispatcher = UnconfinedTestDispatcher(testScheduler))
        sink.writeBatch((0 until FIRST_BATCH_SIZE).map { i -> sample(SAMPLE_NS_BASE + i) })

        sink.writeBatch((0 until SECOND_BATCH_SIZE).map { i -> sample(SAMPLE_NS_BASE + FIRST_BATCH_SIZE + i) })

        val lines = file.readText(Charsets.UTF_8).split('\n').filter { it.isNotEmpty() }
        assertEquals(TOTAL_AFTER_TWO_BATCHES, lines.size)
    }

    @Test
    fun each_line_round_trips_through_json_decoder() = runTest {
        val file = tempFolder.newFile("samples.ndjson")
        val sink = NdjsonBufferSink(file, ioDispatcher = UnconfinedTestDispatcher(testScheduler))
        val originals = listOf(
            sample(SAMPLE_NS_BASE, sourceClock = SOURCE_CLOCK_DEVICE),
            sample(SAMPLE_NS_BASE + 1, sourceClock = SOURCE_CLOCK_GNSS),
        )

        sink.writeBatch(originals)

        val lines = file.readText(Charsets.UTF_8).split('\n').filter { it.isNotEmpty() }
        val decoder = Json { ignoreUnknownKeys = true }
        val decoded = lines.map { decoder.decodeFromString(TelemetrySample.serializer(), it) }
        assertEquals(originals, decoded)
    }

    @Test
    fun lines_are_terminated_with_lf_for_ndjson_compatibility() = runTest {
        val file = tempFolder.newFile("samples.ndjson")
        val sink = NdjsonBufferSink(file, ioDispatcher = UnconfinedTestDispatcher(testScheduler))

        sink.writeBatch(listOf(sample(SAMPLE_NS_BASE)))

        val text = file.readText(Charsets.UTF_8)
        assertTrue("must end with LF — NDJSON readers split on \\n", text.endsWith("\n"))
        assertFalse("CRLF would break naive NDJSON readers", text.contains("\r\n"))
    }

    @Test
    fun empty_batch_is_a_no_op_and_does_not_create_file() = runTest {
        val target = File(tempFolder.root, "empty.ndjson")
        val sink = NdjsonBufferSink(target, ioDispatcher = UnconfinedTestDispatcher(testScheduler))

        sink.writeBatch(emptyList())

        assertFalse("empty batch must not touch disk", target.exists())
    }

    private fun sample(ns: Long, sourceClock: String = SOURCE_CLOCK_DEVICE): TelemetrySample = TelemetrySample(
        tSourceNs = ns,
        tCanonicalNs = ns,
        tSourceClock = sourceClock,
        channels = emptyMap(),
    )
}
