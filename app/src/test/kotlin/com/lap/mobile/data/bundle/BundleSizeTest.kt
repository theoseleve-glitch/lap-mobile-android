package com.lap.mobile.data.bundle

import com.lap.schemas.v1.UUID7
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val FIXTURE_SESSION_ID: UUID7 = "01997333-9d2e-7000-8000-000000000001"
private const val SHORT_FILE_BYTES: Int = 100
private const val MEDIUM_FILE_BYTES: Int = 1_500
private const val EXPECTED_TOTAL: Long = (SHORT_FILE_BYTES + MEDIUM_FILE_BYTES).toLong()

class BundleSizeTest {
    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun totalBytes_sums_every_file_in_the_bundle_dir() {
        val writer = BundleV2Writer(tempRoot = tempFolder.root, sessionId = FIXTURE_SESSION_ID)
        writer.prepare()
        writer.bundleDir.resolve("manifest.json").writeBytes(ByteArray(SHORT_FILE_BYTES))
        writer.bundleDir.resolve("telemetry.csv").writeBytes(ByteArray(MEDIUM_FILE_BYTES))

        val total = BundleSize.totalBytes(writer.bundleDir)

        assertEquals(EXPECTED_TOTAL, total)
    }

    @Test
    fun totalBytes_returns_zero_when_dir_does_not_exist() {
        val total = BundleSize.totalBytes(tempFolder.root.resolve("does-not-exist"))

        assertEquals(0L, total)
    }

    @Test
    fun humanReadable_formats_bytes_below_one_kib() {
        assertEquals("0 B", BundleSize.humanReadable(0L))
        assertEquals("512 B", BundleSize.humanReadable(512L))
        assertEquals("1023 B", BundleSize.humanReadable(1023L))
    }

    @Test
    fun humanReadable_formats_kib_range_with_one_decimal() {
        assertEquals("1.0 KB", BundleSize.humanReadable(1_024L))
        assertEquals("1.5 KB", BundleSize.humanReadable(1_536L))
    }

    @Test
    fun humanReadable_formats_mib_range_with_one_decimal() {
        // Typical lap session bundle size ≈ 1-10 MB; pin the most common range.
        assertEquals("1.0 MB", BundleSize.humanReadable(1_048_576L))
        assertEquals("5.0 MB", BundleSize.humanReadable(5L * 1_048_576L))
    }

    @Test
    fun humanReadable_formats_gib_range_with_two_decimals() {
        // Defensive for an outlier huge multi-hour session.
        assertEquals("1.00 GB", BundleSize.humanReadable(1_073_741_824L))
    }
}
