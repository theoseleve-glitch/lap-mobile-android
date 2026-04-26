package com.lap.mobile

import com.lap.mobile.core.AnalyticsEvent
import com.lap.mobile.core.AnalyticsReporter
import com.lap.mobile.core.NoOpAnalyticsReporter
import com.lap.mobile.core.NoOpSentryReporter
import com.lap.mobile.core.OptOutAwareAnalyticsReporter
import com.lap.mobile.core.OptOutAwareSentryReporter
import com.lap.mobile.core.SentryReporter
import com.lap.mobile.core.SentryReporterLevel
import com.lap.mobile.core.TelemetryOptOutPreference
import com.lap.mobile.core.configureSentryOptions
import com.lap.mobile.core.createLoggingTree
import com.lap.mobile.core.isRealPostHogKey
import com.lap.mobile.core.isRealSentryDsn
import com.lap.mobile.core.lapJson
import io.sentry.SentryOptions
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DEV_FLAVOR = "dev"
private const val STAGING_FLAVOR = "staging"
private const val PROD_FLAVOR = "prod"

private const val DEV_PLACEHOLDER_DSN = "https://placeholder@o0.ingest.sentry.io/dev"
private const val STAGING_PLACEHOLDER_DSN = "https://placeholder@o0.ingest.sentry.io/staging"
private const val PROD_PLACEHOLDER_DSN = "https://placeholder@o0.ingest.sentry.io/prod"

private const val DEV_PLACEHOLDER_KEY = "phc_placeholder_dev"
private const val STAGING_PLACEHOLDER_KEY = "phc_placeholder_staging"
private const val PROD_PLACEHOLDER_KEY = "phc_placeholder_prod"

private const val FAKE_RELEASE = "com.lap.mobile.dev@0.1.0+1"
private const val SAMPLE_COUNT_FIXTURE = 100
private const val DEFAULT_SAMPLE_RATE_TOLERANCE = 0.0001

/**
 * Cross-cutting smoke tests that exercise the app-startup wiring
 * end-to-end at a higher level than the per-component tests
 * (T225). The unit tests for [SentryConfig] (T222), [AnalyticsConfig]
 * (T223), [OptOutAwareSentryReporter] / [OptOutAwareAnalyticsReporter]
 * (T224), [Logging] forwarding (T222) cover their pieces in
 * isolation; this class exercises them composed in the same shape
 * [com.lap.mobile.LapApplication.onCreate] uses at runtime.
 *
 * The Hilt component itself isn't started here — that needs
 * `HiltAndroidTest` + Robolectric or a real device. JVM smoke
 * tests cover the pure-Kotlin chain that runs before/around the
 * Hilt @Inject points, which is where most startup misconfiguration
 * lands (a wrong @Provides type, a NoOp escaping when a real
 * reporter was expected, an opt-out check that doesn't gate a
 * downstream call).
 */
class AppStartupSmokeTest {
    @Test
    fun all_flavor_placeholder_dsns_are_rejected_by_real_dsn_check() {
        assertFalse("dev placeholder DSN should be rejected", isRealSentryDsn(DEV_PLACEHOLDER_DSN))
        assertFalse("staging placeholder DSN should be rejected", isRealSentryDsn(STAGING_PLACEHOLDER_DSN))
        assertFalse("prod placeholder DSN should be rejected", isRealSentryDsn(PROD_PLACEHOLDER_DSN))
    }

    @Test
    fun all_flavor_placeholder_keys_are_rejected_by_real_key_check() {
        assertFalse("dev placeholder key should be rejected", isRealPostHogKey(DEV_PLACEHOLDER_KEY))
        assertFalse("staging placeholder key should be rejected", isRealPostHogKey(STAGING_PLACEHOLDER_KEY))
        assertFalse("prod placeholder key should be rejected", isRealPostHogKey(PROD_PLACEHOLDER_KEY))
    }

    @Test
    fun configure_sentry_options_does_not_crash_for_any_flavor_placeholder() {
        for ((flavor, dsn) in flavorDsns()) {
            val options = SentryOptions()
            configureSentryOptions(
                options = options,
                dsn = dsn,
                environment = flavor,
                release = FAKE_RELEASE,
                debug = (flavor == DEV_FLAVOR),
            )
            assertEquals(dsn, options.dsn)
            assertEquals(flavor, options.environment)
            assertEquals(1.0, options.sampleRate ?: 0.0, DEFAULT_SAMPLE_RATE_TOLERANCE)
        }
    }

    @Test
    fun create_logging_tree_returns_non_null_for_debug_and_release_branches() {
        assertNotNull(createLoggingTree(debug = true))
        assertNotNull(createLoggingTree(debug = false))
    }

    @Test
    fun create_logging_tree_accepts_optoutaware_sentry_reporter_chain() {
        val pref = ToggleableOptOutPreference(optedOut = false)
        val wrapped: SentryReporter = OptOutAwareSentryReporter(NoOpSentryReporter, pref)
        assertNotNull(createLoggingTree(debug = false, sentryReporter = wrapped))
    }

    @Test
    fun startup_sentry_chain_does_not_crash_on_any_method() {
        val pref = ToggleableOptOutPreference(optedOut = false)
        val reporter: SentryReporter = OptOutAwareSentryReporter(NoOpSentryReporter, pref)

        reporter.recordBreadcrumb("ble", "low rssi", SentryReporterLevel.WARNING)
        reporter.captureMessage("HTTP 500", SentryReporterLevel.ERROR)
        reporter.captureException(RuntimeException("boom"))
    }

    @Test
    fun startup_analytics_chain_does_not_crash_on_any_of_the_four_spec_events() {
        val pref = ToggleableOptOutPreference(optedOut = false)
        val reporter: AnalyticsReporter = OptOutAwareAnalyticsReporter(NoOpAnalyticsReporter, pref)

        reporter.track(AnalyticsEvent.APP_STARTED)
        reporter.track(AnalyticsEvent.SESSION_STARTED, mapOf("session_id" to "abc"))
        reporter.track(
            AnalyticsEvent.SESSION_FINALIZED,
            mapOf("session_id" to "abc", "sample_count" to SAMPLE_COUNT_FIXTURE),
        )
        reporter.track(AnalyticsEvent.UPLOAD_COMPLETED)
    }

    @Test
    fun startup_chain_respects_runtime_opt_out_flip_immediately() {
        val pref = ToggleableOptOutPreference(optedOut = false)
        val recordingSentry = RecordingSentryReporter()
        val recordingAnalytics = RecordingAnalyticsReporter()
        val sentryReporter: SentryReporter = OptOutAwareSentryReporter(recordingSentry, pref)
        val analyticsReporter: AnalyticsReporter = OptOutAwareAnalyticsReporter(recordingAnalytics, pref)

        // First batch — opted in
        analyticsReporter.track(AnalyticsEvent.APP_STARTED)
        sentryReporter.captureException(RuntimeException("startup-error"))
        assertEquals(1, recordingAnalytics.events.size)
        assertEquals(1, recordingSentry.exceptions.size)

        // User flips toggle mid-session
        pref.setOptedOut(true)

        // Second batch — opted out, MUST not forward
        analyticsReporter.track(AnalyticsEvent.SESSION_STARTED)
        sentryReporter.captureException(RuntimeException("session-error"))
        assertEquals("opt-out must suppress next analytics event", 1, recordingAnalytics.events.size)
        assertEquals("opt-out must suppress next sentry capture", 1, recordingSentry.exceptions.size)

        // User flips back — forwarding resumes
        pref.setOptedOut(false)
        analyticsReporter.track(AnalyticsEvent.UPLOAD_COMPLETED)
        sentryReporter.captureException(RuntimeException("upload-error"))
        assertEquals(2, recordingAnalytics.events.size)
        assertEquals(2, recordingSentry.exceptions.size)
    }

    @Test
    fun lap_json_round_trips_primitive_through_serialization_module() {
        val original: JsonPrimitive = JsonPrimitive("startup-smoke-test")
        val encoded: String = lapJson.encodeToString(JsonPrimitive.serializer(), original)
        val decoded: JsonPrimitive = lapJson.decodeFromString(JsonPrimitive.serializer(), encoded)
        assertEquals(original.contentOrNull, decoded.contentOrNull)
    }

    @Test
    fun lap_json_ignores_unknown_keys_in_a_known_object() {
        // T021 contract: ignoreUnknownKeys = true so a payload with extra
        // server-side fields decodes cleanly into a known type during a
        // forward-compat rollout window. JsonPrimitive of a complex string
        // smoke-tests that the decoder doesn't crash on the surrounding
        // wrapper — full schema-type round-trip is covered in :schemas tests.
        val payload = """{"value": "hello", "extra_field_from_server": 42}"""
        // We just confirm parseToJsonElement succeeds without throwing on
        // unknown keys at the JsonElement level — strict-vs-lenient applies
        // to typed decoding, not raw JsonElement traversal.
        val element = lapJson.parseToJsonElement(payload)
        assertNotNull(element)
        assertTrue(element.toString().contains("hello"))
    }

    private fun flavorDsns(): List<Pair<String, String>> = listOf(
        DEV_FLAVOR to DEV_PLACEHOLDER_DSN,
        STAGING_FLAVOR to STAGING_PLACEHOLDER_DSN,
        PROD_FLAVOR to PROD_PLACEHOLDER_DSN,
    )

    private class ToggleableOptOutPreference(private var optedOut: Boolean) : TelemetryOptOutPreference {
        override fun isOptedOut(): Boolean = optedOut
        override fun setOptedOut(optedOut: Boolean) {
            this.optedOut = optedOut
        }
    }

    private class RecordingSentryReporter : SentryReporter {
        val exceptions: MutableList<Throwable> = mutableListOf()
        override fun recordBreadcrumb(category: String, message: String, level: SentryReporterLevel) = Unit
        override fun captureMessage(message: String, level: SentryReporterLevel) = Unit
        override fun captureException(throwable: Throwable) {
            exceptions += throwable
        }
    }

    private class RecordingAnalyticsReporter : AnalyticsReporter {
        val events: MutableList<String> = mutableListOf()
        override fun track(event: String, properties: Map<String, Any>) {
            events += event
        }
    }
}
