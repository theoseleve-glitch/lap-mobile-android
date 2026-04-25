package com.lap.mobile.upload

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject

/**
 * Multipart payload for the ingestion endpoint (T151). Built by
 * [BundleMultipartBuilder.build] from a finalized `*.bundle/`
 * directory; consumed by [com.lap.mobile.upload.IngestApi.ingestSession].
 *
 * The shape mirrors the ADR-012 contract:
 *  - `manifest` is the single `manifest.json` file, separately
 *    addressable so the server can validate the session metadata
 *    before it streams chunks to disk.
 *  - `chunks` is every other artifact (telemetry.csv, laps.json,
 *    events.json, checksums.txt — and any future per-time-window
 *    telemetry slices).
 */
data class BundleMultipartPayload(
    val manifest: MultipartBody.Part,
    val chunks: List<MultipartBody.Part>,
)

/**
 * Turns a finalized capture bundle directory into the multipart parts
 * the ingestion endpoint expects (T151).
 *
 * Streaming: each part wraps its file via [asRequestBody], which
 * means the file is read off disk in small segments at write-time —
 * a multi-hour bundle never materialises in heap. `contentLength()`
 * is reported up-front so OkHttp can set `Content-Length` and the
 * server can stream-validate without buffering the whole request.
 *
 * Determinism: chunks are emitted in alphabetical order so a retry
 * (T150 Idempotency-Key + the worker's backoff loop) produces a
 * byte-identical request body, which lets the server safely hash
 * the payload for audit + dedupe.
 *
 * Failure mode: a missing manifest is a hard error (the bundle is
 * corrupted or never finalised) — surfacing it as
 * [IllegalStateException] keeps the upload worker from sending a
 * truncated request the server would reject with a 422.
 */
class BundleMultipartBuilder @Inject constructor() {
    fun build(bundleDir: File): BundleMultipartPayload {
        check(bundleDir.isDirectory) { "bundle dir does not exist: $bundleDir" }
        val manifestFile = File(bundleDir, MANIFEST_FILENAME)
        check(manifestFile.isFile) { "bundle is missing $MANIFEST_FILENAME: $bundleDir" }
        val chunkFiles = bundleDir.listFiles { f -> f.isFile && f.name != MANIFEST_FILENAME }
            ?.sortedBy { it.name }
            .orEmpty()
        return BundleMultipartPayload(
            manifest = filePart(MANIFEST_FIELD, manifestFile),
            chunks = chunkFiles.map { filePart(CHUNKS_FIELD, it) },
        )
    }

    private fun filePart(fieldName: String, file: File): MultipartBody.Part = MultipartBody.Part.createFormData(
        name = fieldName,
        filename = file.name,
        body = file.asRequestBody(OCTET_STREAM.toMediaType()),
    )

    private companion object {
        const val MANIFEST_FILENAME: String = "manifest.json"
        const val MANIFEST_FIELD: String = "manifest"
        const val CHUNKS_FIELD: String = "chunks"

        // application/octet-stream is intentional — the server identifies each
        // chunk by its filename, not by Content-Type. Picking a single uniform
        // type keeps the builder ignorant of artifact-specific MIME concerns.
        const val OCTET_STREAM: String = "application/octet-stream"
    }
}
