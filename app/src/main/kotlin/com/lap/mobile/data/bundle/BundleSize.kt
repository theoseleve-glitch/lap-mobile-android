package com.lap.mobile.data.bundle

import java.io.File

private const val BYTES_PER_KIB: Long = 1_024L
private const val BYTES_PER_MIB: Long = BYTES_PER_KIB * BYTES_PER_KIB
private const val BYTES_PER_GIB: Long = BYTES_PER_KIB * BYTES_PER_MIB

/**
 * Bundle-size accounting (T145). Used by the journal entry written
 * at finalize and the recording-screen footer to surface roughly
 * how much each session weighs — both pieces of UX want the same
 * pre-formatted string so the kernel is hoisted out and JVM-tested
 * here.
 *
 * Journal column + Compose footer wiring is `[PENDING: device-required]`
 * for live screen verification; the kernel ships with the size +
 * formatter API both consumers will call.
 */
object BundleSize {
    fun totalBytes(bundleDir: File): Long {
        if (!bundleDir.isDirectory) return 0L
        return bundleDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * Format a byte count with 1024-base units and a single
     * decimal place in the KiB / MiB ranges (two in GiB). Stays in
     * en-US format (`%.1f` defaults to dot-decimal) so the wire
     * representation logged to the journal is locale-stable; the UI
     * layer can re-format if it wants comma-decimals on a French
     * locale, but the journal stays grep-friendly.
     */
    fun humanReadable(bytes: Long): String = when {
        bytes < BYTES_PER_KIB -> "$bytes B"
        bytes < BYTES_PER_MIB -> "%.1f KB".format(bytes.toDouble() / BYTES_PER_KIB)
        bytes < BYTES_PER_GIB -> "%.1f MB".format(bytes.toDouble() / BYTES_PER_MIB)
        else -> "%.2f GB".format(bytes.toDouble() / BYTES_PER_GIB)
    }
}
