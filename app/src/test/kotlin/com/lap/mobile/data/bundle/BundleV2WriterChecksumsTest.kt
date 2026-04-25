package com.lap.mobile.data.bundle

import com.lap.schemas.v1.UUID7
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

private const val FIXTURE_SESSION_ID: UUID7 = "01997333-9d2e-7000-8000-000000000001"
private const val SHA_256_HEX_LENGTH: Int = 64
private const val EXPECTED_LINE_PARTS: Int = 2

class BundleV2WriterChecksumsTest {
    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun writeChecksums_creates_file_at_checksums_path() {
        val writer = newWriter()
        writer.writeLaps(emptyList())

        writer.writeChecksums()

        assertTrue("checksums.txt must exist", writer.checksumsFile.isFile)
    }

    @Test
    fun checksums_carry_one_line_per_existing_artifact_only() {
        // manifest is intentionally NOT written — checksums file should
        // skip it rather than emit a line for an absent file (the
        // server-side integrity check would otherwise fail looking
        // for a manifest the bundle never had).
        val writer = newWriter()
        writer.writeLaps(emptyList())
        writer.writeEvents(emptyList())

        writer.writeChecksums()

        val lines = writer.checksumsFile.readLines().filter { it.isNotBlank() }
        val files = lines.map { it.substringAfter("  ") }.toSet()
        assertEquals(setOf("laps.json", "events.json"), files)
    }

    @Test
    fun each_checksum_line_matches_sha256_of_the_named_file() {
        val writer = newWriter()
        writer.writeLaps(emptyList())
        writer.writeEvents(emptyList())

        writer.writeChecksums()

        val lines = writer.checksumsFile.readLines().filter { it.isNotBlank() }
        for (line in lines) {
            val parts = line.split("  ", limit = EXPECTED_LINE_PARTS)
            assertEquals("expected '<hex>  <file>' format, got: $line", EXPECTED_LINE_PARTS, parts.size)
            val (recordedHex, fileName) = parts
            val artifact = writer.bundleDir.resolve(fileName)
            assertEquals(
                "checksum mismatch for $fileName — re-read must match recorded hash",
                computeSha256Hex(artifact.readBytes()),
                recordedHex,
            )
        }
    }

    @Test
    fun hex_hash_is_64_lowercase_chars_per_sha256_canonical_format() {
        val writer = newWriter()
        writer.writeLaps(emptyList())

        writer.writeChecksums()

        val line = writer.checksumsFile.readLines().first { it.isNotBlank() }
        val hex = line.substringBefore("  ")
        assertEquals(SHA_256_HEX_LENGTH, hex.length)
        assertTrue(
            "expected lowercase hex hash, got: $hex",
            hex.all { it in '0'..'9' || it in 'a'..'f' },
        )
    }

    private fun computeSha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun newWriter(): BundleV2Writer {
        val writer = BundleV2Writer(tempRoot = tempFolder.root, sessionId = FIXTURE_SESSION_ID)
        writer.prepare()
        return writer
    }
}
