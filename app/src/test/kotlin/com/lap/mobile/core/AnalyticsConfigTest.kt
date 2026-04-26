package com.lap.mobile.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val REAL_KEY = "phc_abc123def456ghi789"
private const val PLACEHOLDER_KEY_DEV = "phc_placeholder_dev"
private const val PLACEHOLDER_KEY_STAGING = "phc_placeholder_staging"
private const val PLACEHOLDER_KEY_PROD = "phc_placeholder_prod"
private const val NON_PHC_PREFIX = "wrong_prefix_abc123"

class AnalyticsConfigTest {
    @Test
    fun isRealPostHogKey_returns_false_for_dev_placeholder() {
        assertFalse(isRealPostHogKey(PLACEHOLDER_KEY_DEV))
    }

    @Test
    fun isRealPostHogKey_returns_false_for_staging_placeholder() {
        assertFalse(isRealPostHogKey(PLACEHOLDER_KEY_STAGING))
    }

    @Test
    fun isRealPostHogKey_returns_false_for_prod_placeholder() {
        assertFalse(isRealPostHogKey(PLACEHOLDER_KEY_PROD))
    }

    @Test
    fun isRealPostHogKey_returns_false_for_blank() {
        assertFalse(isRealPostHogKey(""))
        assertFalse(isRealPostHogKey("   "))
    }

    @Test
    fun isRealPostHogKey_returns_false_for_null() {
        assertFalse(isRealPostHogKey(null))
    }

    @Test
    fun isRealPostHogKey_returns_false_for_non_phc_prefix() {
        assertFalse(isRealPostHogKey(NON_PHC_PREFIX))
    }

    @Test
    fun isRealPostHogKey_returns_true_for_real_key() {
        assertTrue(isRealPostHogKey(REAL_KEY))
    }

    @Test
    fun no_op_reporter_silently_satisfies_interface() {
        NoOpAnalyticsReporter.track(AnalyticsEvent.APP_STARTED)
        NoOpAnalyticsReporter.track(AnalyticsEvent.SESSION_STARTED, mapOf("session_id" to "x"))
        NoOpAnalyticsReporter.track(AnalyticsEvent.SESSION_FINALIZED, mapOf("session_id" to "y", "sample_count" to 42))
        NoOpAnalyticsReporter.track(AnalyticsEvent.UPLOAD_COMPLETED)
    }
}
