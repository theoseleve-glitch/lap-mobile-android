package com.lap.mobile.data.bundle

import java.io.File
import java.security.MessageDigest

private const val SHA_256_ALGORITHM: String = "SHA-256"
private const val SHA_256_BUF_SIZE: Int = 8_192
private const val EXPECTED_LINE_PARTS: Int = 2

/**
 * Outcome of [BundleIntegrityCheck.verify] (T144). [Ok] means the
 * bundle dir holds every artifact named in checksums.txt, none are
 * empty, and every recorded SHA-256 matches the artifact's actual
 * content. [Failed] carries one reason per offence so the upload
 * worker (T146+) can decide whether to retry locally or surface a
 * "bundle damaged" error to the user.
 */
sealed interface BundleIntegrityResult {
    data object Ok : BundleIntegrityResult
    data class Failed(val reasons: List<String>) : BundleIntegrityResult
}

/**
 * Server-side gate replicated client-side (T144). Before T138's
 * atomic rename promotes a `.tmp` to a `.bundle`, the writer has
 * already produced checksums; this check re-reads each artifact
 * from the finalised dir and verifies it matches what the writer
 * thought it was producing.
 *
 * Catches three failure classes:
 *  - **missing checksums.txt** → returns immediately; nothing else
 *    is verifiable without the manifest of expected files.
 *  - **missing artifact** named in checksums but absent on disk
 *    → "missing_file:<name>" reason.
 *  - **empty artifact** (length 0) → "empty_file:<name>" reason.
 *  - **checksum mismatch** → "checksum_mismatch:<name>" reason.
 *
 * Reasons are accumulated (not short-circuited per artifact) so a
 * single verify call surfaces every offence at once — the upload
 * worker can log all of them rather than fail-retry-fail-retry to
 * discover them one at a time.
 */
object BundleIntegrityCheck {
    const val REASON_MISSING_CHECKSUMS: String = "missing_checksums"
    const val REASON_MISSING_FILE: String = "missing_file"
    const val REASON_EMPTY_FILE: String = "empty_file"
    const val REASON_CHECKSUM_MISMATCH: String = "checksum_mismatch"

    fun verify(bundleDir: File): BundleIntegrityResult {
        val checksumsFile = bundleDir.resolve("checksums.txt")
        if (!checksumsFile.isFile) {
            return BundleIntegrityResult.Failed(listOf(REASON_MISSING_CHECKSUMS))
        }
        val expected = checksumsFile.readLines()
            .filter { it.isNotBlank() }
            .map { it.split("  ", limit = EXPECTED_LINE_PARTS) }
        val reasons = mutableListOf<String>()
        for ((expectedHex, fileName) in expected) {
            verifyOne(bundleDir.resolve(fileName), fileName, expectedHex, reasons)
        }
        return if (reasons.isEmpty()) BundleIntegrityResult.Ok else BundleIntegrityResult.Failed(reasons)
    }

    private fun verifyOne(artifact: File, fileName: String, expectedHex: String, reasons: MutableList<String>) {
        if (!artifact.isFile) {
            reasons.add("$REASON_MISSING_FILE:$fileName")
            return
        }
        if (artifact.length() == 0L) {
            reasons.add("$REASON_EMPTY_FILE:$fileName")
            return
        }
        if (sha256Hex(artifact) != expectedHex) {
            reasons.add("$REASON_CHECKSUM_MISMATCH:$fileName")
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance(SHA_256_ALGORITHM)
        file.inputStream().use { stream ->
            val buf = ByteArray(SHA_256_BUF_SIZE)
            while (true) {
                val n = stream.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }
}
