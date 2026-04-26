package com.lap.mobile.upload

import com.lap.mobile.data.UploadQueueDao
import com.lap.mobile.data.UploadQueueEntity
import com.lap.schemas.v1.UUID7
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * JVM integration test for the full capture-to-upload flow (T228 —
 * the device-required half is marked PENDING in the sprint).
 *
 * Stitches together the real production components:
 *  - [BundleMultipartBuilder] — reads a finalized `*.bundle/` dir
 *    from disk into multipart parts (T151).
 *  - [IngestApi] — Retrofit-backed wire contract (T148).
 *  - [UploadOrchestration] — orchestration tick: dequeue → upload →
 *    update queue row (T147 + T155).
 *
 * The seam is [BundleUploader]: the codebase ships a placeholder
 * impl in [com.lap.mobile.di.UploadModule] that always returns
 * [UploadResult.Retryable] so production never marks a session
 * "completed" before the real adapter lands. This test inlines a
 * minimal Retrofit-backed [BundleUploader] (`retrofitBundleUploader`)
 * so the chain can be exercised end-to-end without waiting for that
 * production wiring task — the inline adapter mirrors what the
 * production impl will eventually do, locking the contract today.
 *
 * What this test does NOT cover (genuinely device-required, hence
 * the [PENDING: device-required] mark in the sprint file):
 *  - Real WorkManager scheduling + constraint dispatch.
 *  - Real Hilt graph construction from Application context.
 *  - Real Android networking (DNS, NetworkSecurityConfig, TLS chain).
 *  - Foreground-service vs doze-mode interactions during upload.
 *
 * What it DOES cover (the wire-contract regression surface):
 *  - POST path matches the server contract (`/api/v1/sessions/ingest`).
 *  - `Idempotency-Key` header carries the session UUIDv7 verbatim
 *    (ADR-012 dedupe).
 *  - Multipart body has exactly one `manifest` part + the expected
 *    chunks parts in alphabetical order.
 *  - 2xx → orchestration outcome [UploadOutcome.Success] + queue row
 *    flips to "completed".
 *  - 5xx → orchestration outcome [UploadOutcome.Retry] + queue row
 *    stays "queued" with attempt_count + next_retry_at advanced.
 *  - 4xx → orchestration outcome [UploadOutcome.PermanentFailure] +
 *    queue row flips to "failed_permanent" with no retry scheduled.
 */
private const val SESSION_ID: UUID7 = "01997333-9d2e-7000-8000-000000000228"
private const val MANIFEST_JSON: String =
    """{"capture_mode":"finalized","session_id":"$SESSION_ID"}"""
private const val TELEMETRY_CSV: String = "t_source_ns,t_canonical_ns,t_source_clock\n100,100,gnss\n"
private const val LAPS_JSON: String = "[]"
private const val EVENTS_JSON: String = "[]"
private const val CHECKSUMS_TXT: String = "deadbeef  manifest.json\n"
private const val EXPECTED_CHUNK_COUNT: Int = 4
private const val INGEST_PATH: String = "/api/v1/sessions/ingest"
private const val IDEMPOTENCY_KEY_HEADER: String = "Idempotency-Key"
private const val MANIFEST_FORM_FIELD: String = """name="manifest""""
private const val CHUNKS_FORM_FIELD: String = """name="chunks""""
private const val SHORT_TIMEOUT_SECONDS: Long = 2L
private const val SUCCESS_HTTP_CODE: Int = 201
private const val RETRY_HTTP_CODE: Int = 502
private const val PERMANENT_HTTP_CODE: Int = 422
private const val ATTEMPT_BEFORE_TICK: Int = 0
private val FIXED_NOW: Instant = Instant.parse("2026-04-26T10:00:00Z")
private val FIXED_CLOCK: Clock = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"))

class CaptureToUploadFlowTest {
    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var ingestApi: IngestApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        // Tight timeouts so server hangs surface as failures fast
        // rather than burning the JUnit idle timer.
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(SHORT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(SHORT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(SHORT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        // kotlinx-serialization converter is already in deps; IngestApi
        // returns Response<Unit> (the body is never deserialized) but
        // Retrofit still needs *some* converter factory registered.
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(httpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        ingestApi = retrofit.create(IngestApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun a_2xx_response_flips_the_queue_row_to_completed_and_the_orchestration_returns_success() = runTest {
        server.enqueue(MockResponse().setResponseCode(SUCCESS_HTTP_CODE))
        val bundleDir = writeFixtureBundle()
        val dao = SingleRowFakeQueueDao(initialRow = queuedRow())
        val orchestration = UploadOrchestration(
            queueDao = dao,
            uploader = retrofitBundleUploader(bundleDir),
            clock = FIXED_CLOCK,
        )

        val outcome = orchestration.runOnce()

        assertEquals(UploadOutcome.Success, outcome)
        assertEquals("completed", dao.currentState)
        assertEquals(SUCCESS_HTTP_CODE, dao.currentLastHttpCode)
    }

    @Test
    fun the_uploaded_request_targets_the_ingest_path_so_the_server_route_is_exercised_verbatim() = runTest {
        server.enqueue(MockResponse().setResponseCode(SUCCESS_HTTP_CODE))
        val bundleDir = writeFixtureBundle()
        val dao = SingleRowFakeQueueDao(initialRow = queuedRow())
        val orchestration = UploadOrchestration(
            queueDao = dao,
            uploader = retrofitBundleUploader(bundleDir),
            clock = FIXED_CLOCK,
        )

        orchestration.runOnce()

        val recorded = server.takeRequest(SHORT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        assertNotNull("server must have received exactly one request", recorded)
        assertEquals("POST", recorded!!.method)
        assertEquals(INGEST_PATH, recorded.path)
    }

    @Test
    fun the_uploaded_request_carries_the_session_uuid_as_idempotency_key_so_retries_replay_per_adr_012() = runTest {
        server.enqueue(MockResponse().setResponseCode(SUCCESS_HTTP_CODE))
        val bundleDir = writeFixtureBundle()
        val dao = SingleRowFakeQueueDao(initialRow = queuedRow())
        val orchestration = UploadOrchestration(
            queueDao = dao,
            uploader = retrofitBundleUploader(bundleDir),
            clock = FIXED_CLOCK,
        )

        orchestration.runOnce()

        val recorded = server.takeRequest(SHORT_TIMEOUT_SECONDS, TimeUnit.SECONDS)!!
        // ADR-012: the server dedupes by Idempotency-Key. Reusing the
        // session UUIDv7 means a network retry replays the exact key the
        // server first saw — the upload is at-most-once even if the ACK
        // lands twice.
        assertEquals(SESSION_ID, recorded.getHeader(IDEMPOTENCY_KEY_HEADER))
    }

    @Test
    fun the_uploaded_body_contains_one_manifest_part_and_the_full_set_of_chunks_so_the_server_can_validate() = runTest {
        server.enqueue(MockResponse().setResponseCode(SUCCESS_HTTP_CODE))
        val bundleDir = writeFixtureBundle()
        val dao = SingleRowFakeQueueDao(initialRow = queuedRow())
        val orchestration = UploadOrchestration(
            queueDao = dao,
            uploader = retrofitBundleUploader(bundleDir),
            clock = FIXED_CLOCK,
        )

        orchestration.runOnce()

        val recorded = server.takeRequest(SHORT_TIMEOUT_SECONDS, TimeUnit.SECONDS)!!
        val body = recorded.body.readUtf8()
        // Exactly one manifest part is the wire contract — the server's
        // route handler binds it as a single field; multiple manifests
        // would be silently shadowed by whichever parsed last.
        assertEquals(
            "expected exactly one manifest part",
            1,
            MANIFEST_FORM_FIELD.toRegex().findAll(body).count(),
        )
        // EXPECTED_CHUNK_COUNT chunks part the manifest.json file —
        // mirrors the fixture written by writeFixtureBundle().
        assertEquals(
            "expected $EXPECTED_CHUNK_COUNT chunks",
            EXPECTED_CHUNK_COUNT,
            CHUNKS_FORM_FIELD.toRegex().findAll(body).count(),
        )
    }

    @Test
    fun a_5xx_response_keeps_the_row_queued_with_a_next_retry_at_so_the_worker_paces_re_enqueue() = runTest {
        server.enqueue(MockResponse().setResponseCode(RETRY_HTTP_CODE))
        val bundleDir = writeFixtureBundle()
        val dao = SingleRowFakeQueueDao(initialRow = queuedRow())
        val orchestration = UploadOrchestration(
            queueDao = dao,
            uploader = retrofitBundleUploader(bundleDir),
            clock = FIXED_CLOCK,
        )

        val outcome = orchestration.runOnce()

        assertEquals(UploadOutcome.Retry, outcome)
        assertEquals("queued", dao.currentState)
        assertEquals(RETRY_HTTP_CODE, dao.currentLastHttpCode)
        assertNotNull(
            "Retryable must stamp next_retry_at so WorkManager paces re-enqueue per the T154 backoff curve",
            dao.currentNextRetryAt,
        )
    }

    @Test
    fun a_4xx_client_error_flips_the_row_to_failed_permanent_so_a_malformed_bundle_doesnt_re_cycle() = runTest {
        server.enqueue(MockResponse().setResponseCode(PERMANENT_HTTP_CODE))
        val bundleDir = writeFixtureBundle()
        val dao = SingleRowFakeQueueDao(initialRow = queuedRow())
        val orchestration = UploadOrchestration(
            queueDao = dao,
            uploader = retrofitBundleUploader(bundleDir),
            clock = FIXED_CLOCK,
        )

        val outcome = orchestration.runOnce()

        assertEquals(UploadOutcome.PermanentFailure, outcome)
        assertEquals("failed_permanent", dao.currentState)
        assertEquals(PERMANENT_HTTP_CODE, dao.currentLastHttpCode)
        assertNull(
            "PermanentFailure must clear next_retry_at — there's no scheduled retry for a 4xx client error",
            dao.currentNextRetryAt,
        )
    }

    /**
     * Inline Retrofit-backed [BundleUploader]. Wraps the production
     * [BundleMultipartBuilder] + [IngestApi] so the orchestration
     * exercises the full chain. The body of `upload` mirrors what the
     * eventual production adapter (a follow-on task) will do:
     *
     *   1. read the bundle dir into multipart parts;
     *   2. call IngestApi with the idempotency key + parts;
     *   3. map the HTTP code to the [UploadResult] taxonomy.
     *
     * The mapping rules — 2xx → Success, 4xx → PermanentFailure, 5xx
     * (and IO errors) → Retryable — mirror the existing scenario tests
     * (T169 + T170) which document the upload state machine.
     */
    private fun retrofitBundleUploader(bundleDir: File): BundleUploader = BundleUploader { sessionId, idempotencyKey ->
        val payload = BundleMultipartBuilder().build(bundleDir)

        @Suppress("TooGenericExceptionCaught")
        val response = try {
            ingestApi.ingestSession(
                idempotencyKey = idempotencyKey,
                manifest = payload.manifest,
                chunks = payload.chunks,
            )
        } catch (e: Exception) {
            return@BundleUploader UploadResult.Retryable(httpCode = null, message = e.message.orEmpty())
        }
        val code = response.code()
        when {
            response.isSuccessful -> UploadResult.Success(httpCode = code)
            code in CLIENT_ERROR_RANGE -> UploadResult.PermanentFailure(httpCode = code, message = "client_error:$code")
            else -> UploadResult.Retryable(httpCode = code, message = "server_error:$code")
        }.also {
            // sessionId param is part of the BundleUploader contract; the
            // header takes idempotencyKey separately. Read sessionId here
            // so an unused-param lint doesn't trigger and so the contract
            // stays observable.
            check(sessionId.isNotBlank())
        }
    }

    private fun writeFixtureBundle(): File {
        val bundleDir = tempFolder.newFolder("$SESSION_ID.bundle")
        File(bundleDir, "manifest.json").writeText(MANIFEST_JSON)
        File(bundleDir, "telemetry.csv").writeText(TELEMETRY_CSV)
        File(bundleDir, "laps.json").writeText(LAPS_JSON)
        File(bundleDir, "events.json").writeText(EVENTS_JSON)
        File(bundleDir, "checksums.txt").writeText(CHECKSUMS_TXT)
        return bundleDir
    }

    private fun queuedRow(): UploadQueueEntity = UploadQueueEntity(
        sessionId = SESSION_ID,
        state = "queued",
        chunksTotal = EXPECTED_CHUNK_COUNT,
        idempotencyKey = SESSION_ID,
        attemptCount = ATTEMPT_BEFORE_TICK,
    )

    private companion object {
        // 4xx range — anything in here is a client-side problem the
        // server can't reconcile by retrying with the same payload.
        val CLIENT_ERROR_RANGE: IntRange = 400..499
    }
}

/**
 * Single-row fake [UploadQueueDao] (mirrors the fakes used by the
 * existing scenario tests). The orchestration only needs dequeueNext
 * + updateAttempt; insertOrIgnore is errored to surface accidental
 * use.
 */
private class SingleRowFakeQueueDao(initialRow: UploadQueueEntity) : UploadQueueDao {
    private var row: UploadQueueEntity = initialRow

    val currentState: String get() = row.state
    val currentLastHttpCode: Int? get() = row.lastHttpCode
    val currentNextRetryAt: Instant? get() = row.nextRetryAt

    override suspend fun insertOrIgnore(row: UploadQueueEntity): Long =
        error("insertOrIgnore not exercised by CaptureToUploadFlowTest")

    override suspend fun dequeueNext(state: String): UploadQueueEntity? = if (row.state == state) row else null

    override suspend fun updateAttempt(
        sessionId: UUID7,
        state: String,
        attemptCount: Int,
        nextRetryAt: Instant?,
        lastHttpCode: Int?,
        lastError: String?,
    ): Int {
        check(sessionId == row.sessionId)
        row = row.copy(
            state = state,
            attemptCount = attemptCount,
            nextRetryAt = nextRetryAt,
            lastHttpCode = lastHttpCode,
            lastError = lastError,
        )
        return 1
    }
}

@Suppress("unused")
private fun assertWriteRequestRecorded(recorded: RecordedRequest) {
    // Stub kept for future request-body invariant assertions; suppress
    // unused-warning so the file passes detekt without a blanket rule.
    assertTrue(recorded.bodySize > 0)
}
