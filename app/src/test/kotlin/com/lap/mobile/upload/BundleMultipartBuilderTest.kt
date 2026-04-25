package com.lap.mobile.upload

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private const val FIXTURE_SESSION_ID: String = "01997333-9d2e-7000-8000-000000000001"
private const val MANIFEST_JSON: String =
    """{"capture_mode":"finalized","session_id":"$FIXTURE_SESSION_ID"}"""
private const val TELEMETRY_CSV: String = "t_source_ns,t_canonical_ns,t_source_clock\n100,100,gnss\n200,200,gnss\n"
private const val LAPS_JSON: String = "[]"
private const val EVENTS_JSON: String = "[]"
private const val CHECKSUMS_TXT: String = "deadbeef  manifest.json\n"
private const val FORM_FIELD_NAME_REGEX_PART: String = """name="(?<name>[^"]+)""""
private const val FILENAME_REGEX_PART: String = """filename="(?<filename>[^"]+)""""

class BundleMultipartBuilderTest {
    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun manifest_part_uses_form_data_field_name_manifest_so_the_server_handler_can_bind_it() {
        val bundleDir = writeFixtureBundle()

        val payload = BundleMultipartBuilder().build(bundleDir)

        assertEquals("manifest", formFieldName(payload.manifest))
    }

    @Test
    fun manifest_part_preserves_manifest_json_filename_for_server_side_audit_logs() {
        val bundleDir = writeFixtureBundle()

        val payload = BundleMultipartBuilder().build(bundleDir)

        assertEquals("manifest.json", formFieldFilename(payload.manifest))
    }

    @Test
    fun chunks_list_excludes_manifest_so_it_is_not_uploaded_twice() {
        val bundleDir = writeFixtureBundle()

        val payload = BundleMultipartBuilder().build(bundleDir)

        val chunkFilenames = payload.chunks.map { formFieldFilename(it) }
        assertTrue(
            "manifest.json must not appear in chunks; got $chunkFilenames",
            "manifest.json" !in chunkFilenames,
        )
    }

    @Test
    fun chunks_list_includes_every_non_manifest_artifact_file_in_the_bundle_dir() {
        val bundleDir = writeFixtureBundle()

        val payload = BundleMultipartBuilder().build(bundleDir)

        val chunkFilenames = payload.chunks.map { formFieldFilename(it) }.toSet()
        assertEquals(
            setOf("checksums.txt", "events.json", "laps.json", "telemetry.csv"),
            chunkFilenames,
        )
    }

    @Test
    fun every_chunk_part_uses_form_data_field_name_chunks_so_the_server_collects_them_as_a_repeated_field() {
        val bundleDir = writeFixtureBundle()

        val payload = BundleMultipartBuilder().build(bundleDir)

        val distinctNames = payload.chunks.map { formFieldName(it) }.toSet()
        assertEquals(setOf("chunks"), distinctNames)
    }

    @Test
    fun chunks_are_ordered_alphabetically_so_a_retry_replays_the_same_byte_sequence() {
        val bundleDir = writeFixtureBundle()

        val payload = BundleMultipartBuilder().build(bundleDir)

        val chunkFilenames = payload.chunks.map { formFieldFilename(it) }
        assertEquals(chunkFilenames.sorted(), chunkFilenames)
    }

    @Test
    fun part_content_length_equals_file_size_so_the_payload_streams_from_disk_not_memory() {
        // RequestBody backed by File.asRequestBody() reports contentLength()
        // = file.length() and writes the file by streaming. A bundle bigger
        // than heap (multi-hour session) must not OOM the upload worker.
        val bundleDir = writeFixtureBundle()

        val payload = BundleMultipartBuilder().build(bundleDir)

        for (chunk in payload.chunks) {
            val filename = formFieldFilename(chunk)
            val expectedSize = bundleDir.resolve(filename).length()
            assertEquals(
                "chunk $filename must report file size as contentLength so it streams",
                expectedSize,
                chunk.body.contentLength(),
            )
        }
    }

    @Test
    fun part_body_writes_the_files_exact_bytes_so_round_trip_is_lossless() {
        val bundleDir = writeFixtureBundle()

        val payload = BundleMultipartBuilder().build(bundleDir)

        val chunk = payload.chunks.first { formFieldFilename(it) == "telemetry.csv" }
        val sink = Buffer()
        chunk.body.writeTo(sink)
        assertEquals(TELEMETRY_CSV, sink.readUtf8())
    }

    @Test
    fun missing_manifest_throws_so_we_do_not_silently_upload_a_broken_bundle() {
        val bundleDir = writeFixtureBundle()
        bundleDir.resolve("manifest.json").delete()

        assertThrows(IllegalStateException::class.java) {
            BundleMultipartBuilder().build(bundleDir)
        }
    }

    @Test
    fun bundle_dir_does_not_exist_throws_so_a_caller_bug_surfaces_loudly() {
        val ghostDir = tempFolder.newFolder("ghost").apply { delete() }

        assertThrows(IllegalStateException::class.java) {
            BundleMultipartBuilder().build(ghostDir)
        }
    }

    private fun writeFixtureBundle(): File {
        val dir = tempFolder.newFolder("session-fixture.bundle")
        dir.resolve("manifest.json").writeText(MANIFEST_JSON)
        dir.resolve("telemetry.csv").writeText(TELEMETRY_CSV)
        dir.resolve("laps.json").writeText(LAPS_JSON)
        dir.resolve("events.json").writeText(EVENTS_JSON)
        dir.resolve("checksums.txt").writeText(CHECKSUMS_TXT)
        return dir
    }

    private fun formFieldName(part: okhttp3.MultipartBody.Part): String {
        val disposition = requireNotNull(part.headers?.get("Content-Disposition")) {
            "Content-Disposition header missing on part: ${part.headers}"
        }
        val match = Regex(FORM_FIELD_NAME_REGEX_PART).find(disposition)
        assertNotNull("Content-Disposition has no name=: $disposition", match)
        return match!!.groups["name"]!!.value
    }

    private fun formFieldFilename(part: okhttp3.MultipartBody.Part): String {
        val disposition = requireNotNull(part.headers?.get("Content-Disposition")) {
            "Content-Disposition header missing on part: ${part.headers}"
        }
        val match = Regex(FILENAME_REGEX_PART).find(disposition)
        assertNotNull("Content-Disposition has no filename=: $disposition", match)
        return match!!.groups["filename"]!!.value
    }
}
