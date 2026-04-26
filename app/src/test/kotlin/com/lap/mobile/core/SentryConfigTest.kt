package com.lap.mobile.core

import io.sentry.SentryOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val REAL_DSN = "https://abc123@o12345.ingest.sentry.io/789"
private const val PLACEHOLDER_DSN_DEV = "https://placeholder@o0.ingest.sentry.io/dev"
private const val PLACEHOLDER_DSN_STAGING = "https://placeholder@o0.ingest.sentry.io/staging"
private const val PLACEHOLDER_DSN_PROD = "https://placeholder@o0.ingest.sentry.io/prod"

private const val ENV_DEV = "dev"
private const val ENV_PROD = "prod"

private const val RELEASE_DEV = "com.lap.mobile.dev@0.1.0-dev+1"
private const val RELEASE_PROD = "com.lap.mobile@0.1.0+1"

private const val DEFAULT_SAMPLE_RATE_TOLERANCE = 0.0001
private const val EXPLICIT_SAMPLE_RATE = 0.25

class SentryConfigTest {
    @Test
    fun configureSentryOptions_sets_dsn() {
        val options = SentryOptions()
        configureSentryOptions(options, dsn = REAL_DSN, environment = ENV_DEV, release = RELEASE_DEV, debug = true)
        assertEquals(REAL_DSN, options.dsn)
    }

    @Test
    fun configureSentryOptions_sets_environment() {
        val options = SentryOptions()
        configureSentryOptions(options, dsn = REAL_DSN, environment = ENV_PROD, release = RELEASE_PROD, debug = false)
        assertEquals(ENV_PROD, options.environment)
    }

    @Test
    fun configureSentryOptions_sets_release() {
        val options = SentryOptions()
        configureSentryOptions(options, dsn = REAL_DSN, environment = ENV_DEV, release = RELEASE_DEV, debug = true)
        assertEquals(RELEASE_DEV, options.release)
    }

    @Test
    fun configureSentryOptions_sets_debug_true_for_debug_build() {
        val options = SentryOptions()
        configureSentryOptions(options, dsn = REAL_DSN, environment = ENV_DEV, release = RELEASE_DEV, debug = true)
        assertTrue(options.isDebug)
    }

    @Test
    fun configureSentryOptions_sets_debug_false_for_release_build() {
        val options = SentryOptions()
        configureSentryOptions(options, dsn = REAL_DSN, environment = ENV_PROD, release = RELEASE_PROD, debug = false)
        assertFalse(options.isDebug)
    }

    @Test
    fun configureSentryOptions_default_sample_rate_is_full() {
        val options = SentryOptions()
        configureSentryOptions(options, dsn = REAL_DSN, environment = ENV_DEV, release = RELEASE_DEV, debug = true)
        assertEquals(1.0, options.sampleRate ?: 0.0, DEFAULT_SAMPLE_RATE_TOLERANCE)
    }

    @Test
    fun configureSentryOptions_accepts_explicit_sample_rate() {
        val options = SentryOptions()
        configureSentryOptions(
            options,
            dsn = REAL_DSN,
            environment = ENV_PROD,
            release = RELEASE_PROD,
            debug = false,
            sampleRate = EXPLICIT_SAMPLE_RATE,
        )
        assertEquals(EXPLICIT_SAMPLE_RATE, options.sampleRate ?: 0.0, DEFAULT_SAMPLE_RATE_TOLERANCE)
    }

    @Test
    fun isRealSentryDsn_returns_false_for_dev_placeholder() {
        assertFalse(isRealSentryDsn(PLACEHOLDER_DSN_DEV))
    }

    @Test
    fun isRealSentryDsn_returns_false_for_staging_placeholder() {
        assertFalse(isRealSentryDsn(PLACEHOLDER_DSN_STAGING))
    }

    @Test
    fun isRealSentryDsn_returns_false_for_prod_placeholder() {
        assertFalse(isRealSentryDsn(PLACEHOLDER_DSN_PROD))
    }

    @Test
    fun isRealSentryDsn_returns_false_for_blank() {
        assertFalse(isRealSentryDsn(""))
        assertFalse(isRealSentryDsn("   "))
    }

    @Test
    fun isRealSentryDsn_returns_false_for_null() {
        assertFalse(isRealSentryDsn(null))
    }

    @Test
    fun isRealSentryDsn_returns_true_for_real_dsn() {
        assertTrue(isRealSentryDsn(REAL_DSN))
    }
}
