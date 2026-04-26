package com.lap.mobile.core

import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DUMMY_CATEGORY = "ble"
private const val DUMMY_MESSAGE = "low rssi"
private const val NETWORK_ERROR_MESSAGE = "HTTP 500"
private const val UPLOAD_CATEGORY = "upload"
private const val ASSERT_CATEGORY = "core"
private const val ASSERT_MESSAGE = "invariant violated"

/**
 * JVM tests on the pure [forwardToSentry] function (T222).
 *
 * Only the routing policy is exercised here — the
 * Logcat-side-effect path inside `ReleaseLoggingTree.log()` is not
 * tested because [android.util.Log.println] throws "Method not
 * mocked" on the unit-test JVM. Splitting the routing into a
 * standalone internal function lets us lock the WARN-→breadcrumb /
 * ERROR-→capture invariant without spinning up Robolectric.
 */
class LoggingForwardTest {
    @Test
    fun warn_log_records_breadcrumb_at_warning_level() {
        val fake = RecordingSentryReporter()
        forwardToSentry(fake, priority = Log.WARN, category = DUMMY_CATEGORY, message = DUMMY_MESSAGE, throwable = null)
        assertEquals(1, fake.breadcrumbs.size)
        val breadcrumb = fake.breadcrumbs.single()
        assertEquals(DUMMY_CATEGORY, breadcrumb.category)
        assertEquals(DUMMY_MESSAGE, breadcrumb.message)
        assertEquals(SentryReporterLevel.WARNING, breadcrumb.level)
        assertTrue(fake.exceptions.isEmpty())
        assertTrue(fake.messages.isEmpty())
    }

    @Test
    fun error_log_with_throwable_captures_exception() {
        val fake = RecordingSentryReporter()
        val ex = RuntimeException("boom")
        forwardToSentry(
            reporter = fake,
            priority = Log.ERROR,
            category = UPLOAD_CATEGORY,
            message = NETWORK_ERROR_MESSAGE,
            throwable = ex,
        )
        assertEquals(1, fake.exceptions.size)
        assertSame(ex, fake.exceptions.single())
        assertTrue(fake.breadcrumbs.isEmpty())
        assertTrue(fake.messages.isEmpty())
    }

    @Test
    fun error_log_without_throwable_captures_message() {
        val fake = RecordingSentryReporter()
        forwardToSentry(
            reporter = fake,
            priority = Log.ERROR,
            category = UPLOAD_CATEGORY,
            message = NETWORK_ERROR_MESSAGE,
            throwable = null,
        )
        assertEquals(1, fake.messages.size)
        val msg = fake.messages.single()
        assertEquals(NETWORK_ERROR_MESSAGE, msg.message)
        assertEquals(SentryReporterLevel.ERROR, msg.level)
        assertTrue(fake.exceptions.isEmpty())
        assertTrue(fake.breadcrumbs.isEmpty())
    }

    @Test
    fun assert_log_with_throwable_captures_exception() {
        val fake = RecordingSentryReporter()
        val ex = IllegalStateException(ASSERT_MESSAGE)
        forwardToSentry(
            reporter = fake,
            priority = Log.ASSERT,
            category = ASSERT_CATEGORY,
            message = ASSERT_MESSAGE,
            throwable = ex,
        )
        assertEquals(1, fake.exceptions.size)
        assertSame(ex, fake.exceptions.single())
    }

    @Test
    fun info_log_does_not_forward() {
        val fake = RecordingSentryReporter()
        forwardToSentry(fake, priority = Log.INFO, category = "ui", message = "tap", throwable = null)
        assertTrue(fake.breadcrumbs.isEmpty())
        assertTrue(fake.messages.isEmpty())
        assertTrue(fake.exceptions.isEmpty())
    }

    @Test
    fun debug_log_does_not_forward() {
        val fake = RecordingSentryReporter()
        forwardToSentry(fake, priority = Log.DEBUG, category = "ui", message = "tap", throwable = null)
        assertTrue(fake.breadcrumbs.isEmpty())
        assertTrue(fake.messages.isEmpty())
        assertTrue(fake.exceptions.isEmpty())
    }

    @Test
    fun no_op_reporter_silently_satisfies_interface() {
        NoOpSentryReporter.recordBreadcrumb("a", "b", SentryReporterLevel.INFO)
        NoOpSentryReporter.captureMessage("c", SentryReporterLevel.ERROR)
        NoOpSentryReporter.captureException(RuntimeException("boom"))
    }

    private class RecordingSentryReporter : SentryReporter {
        val breadcrumbs = mutableListOf<RecordedBreadcrumb>()
        val messages = mutableListOf<RecordedMessage>()
        val exceptions = mutableListOf<Throwable>()

        override fun recordBreadcrumb(category: String, message: String, level: SentryReporterLevel) {
            breadcrumbs += RecordedBreadcrumb(category, message, level)
        }

        override fun captureMessage(message: String, level: SentryReporterLevel) {
            messages += RecordedMessage(message, level)
        }

        override fun captureException(throwable: Throwable) {
            exceptions += throwable
        }
    }

    private data class RecordedBreadcrumb(val category: String, val message: String, val level: SentryReporterLevel)
    private data class RecordedMessage(val message: String, val level: SentryReporterLevel)
}
