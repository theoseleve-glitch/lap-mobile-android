package com.lap.mobile.data

import com.lap.schemas.v1.TelemetrySample
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

/**
 * On-disk format chosen for [CrashSafeBuffer] (T080):
 * **newline-delimited JSON** (NDJSON, RFC-less but widely supported).
 *
 * One JSON-encoded [TelemetrySample] per line, UTF-8, terminated with
 * `\n` (LF — never CRLF). The file is opened in append mode and
 * `fsync`'d at the end of every batch so a process kill mid-recording
 * loses at most the in-memory tail not yet handed to the sink — the
 * partner of the in-memory bound enforced by [CrashSafeBuffer].
 *
 * Why NDJSON over length-prefixed binary:
 *  - **Recoverable**: a truncated last line is the only damage from a
 *    crash, and a forgiving reader can drop it and replay everything
 *    before. Length-prefixed binary makes a single bad length byte
 *    unrecoverable until the next sync point.
 *  - **Append-only friendly**: each line is independent — no header,
 *    no trailer, no per-file CRC to update on every batch.
 *  - **Schema-agile**: `ignoreUnknownKeys = true` means a future
 *    [TelemetrySample] field added server-side won't break older
 *    on-device readers (T131 bundle writer; T133+ upload).
 *  - **Inspectable**: `cat` / `tail -f` / `jq` work for forensics
 *    without a custom decoder.
 *  - **Cost**: extra bytes (`{"tSourceNs":...}` vs varint+payload),
 *    accepted because mobile bundles compress well downstream.
 *
 * Recovery contract for downstream readers (bundle writer T131,
 * forensic tools): split on `\n`, skip empty lines, attempt
 * `Json.decodeFromString` per line, drop lines that fail to parse
 * (only the trailing line should ever fail under crash semantics).
 */
class NdjsonBufferSink(
    private val file: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val json: Json = NDJSON_FORMAT,
) : BufferSink {
    override suspend fun writeBatch(batch: List<TelemetrySample>) {
        if (batch.isEmpty()) return
        withContext(ioDispatcher) {
            // append mode — crash-safe accumulation across successive batches
            FileOutputStream(file, APPEND_MODE).use { fos ->
                val buffer = StringBuilder()
                for (sample in batch) {
                    buffer.append(json.encodeToString(TelemetrySample.serializer(), sample)).append('\n')
                }
                fos.write(buffer.toString().toByteArray(Charsets.UTF_8))
                fos.flush()
                fos.fd.sync()
            }
        }
    }
}

private const val APPEND_MODE: Boolean = true

/**
 * Single shared [Json] instance — no pretty-printing (one sample per
 * line is the NDJSON contract), tolerant of unknown fields so a future
 * server-side schema bump doesn't break replay readers built against
 * the older shape.
 */
internal val NDJSON_FORMAT: Json = Json {
    prettyPrint = false
    ignoreUnknownKeys = true
}
