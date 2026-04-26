package com.lap.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CATEGORY = "ble"
private const val MESSAGE = "low rssi"
private const val ERROR_MESSAGE = "HTTP 500"

/**
 * JVM tests for [OptOutAwareSentryReporter] (T224).
 *
 * The wrapper is a thin pass-through that gates every delegate
 * call on a [TelemetryOptOutPreference] check. Tests cover both
 * sides of the gate per method to lock the "respected" invariant
 * — when the user opts out, no call leaks through to the
 * underlying [SentryReporter].
 */
class OptOutAwareSentryReporterTest {
    @Test
    fun forwards_breadcrumb_when_opted_in() {
        val pref = FakeOptOutPreference(optedOut = false)
        val delegate = RecordingSentryReporter()
        val reporter = OptOutAwareSentryReporter(delegate, pref)

        reporter.recordBreadcrumb(CATEGORY, MESSAGE, SentryReporterLevel.WARNING)

        assertEquals(1, delegate.breadcrumbs.size)
        val breadcrumb = delegate.breadcrumbs.single()
        assertEquals(CATEGORY, breadcrumb.category)
        assertEquals(MESSAGE, breadcrumb.message)
        assertEquals(SentryReporterLevel.WARNING, breadcrumb.level)
    }

    @Test
    fun forwards_message_when_opted_in() {
        val pref = FakeOptOutPreference(optedOut = false)
        val delegate = RecordingSentryReporter()
        val reporter = OptOutAwareSentryReporter(delegate, pref)

        reporter.captureMessage(ERROR_MESSAGE, SentryReporterLevel.ERROR)

        assertEquals(1, delegate.messages.size)
        assertEquals(ERROR_MESSAGE, delegate.messages.single().message)
    }

    @Test
    fun forwards_exception_when_opted_in() {
        val pref = FakeOptOutPreference(optedOut = false)
        val delegate = RecordingSentryReporter()
        val reporter = OptOutAwareSentryReporter(delegate, pref)
        val ex = RuntimeException("boom")

        reporter.captureException(ex)

        assertEquals(1, delegate.exceptions.size)
        assertSame(ex, delegate.exceptions.single())
    }

    @Test
    fun suppresses_breadcrumb_when_opted_out() {
        val pref = FakeOptOutPreference(optedOut = true)
        val delegate = RecordingSentryReporter()
        val reporter = OptOutAwareSentryReporter(delegate, pref)

        reporter.recordBreadcrumb(CATEGORY, MESSAGE, SentryReporterLevel.WARNING)

        assertTrue(delegate.breadcrumbs.isEmpty())
    }

    @Test
    fun suppresses_message_when_opted_out() {
        val pref = FakeOptOutPreference(optedOut = true)
        val delegate = RecordingSentryReporter()
        val reporter = OptOutAwareSentryReporter(delegate, pref)

        reporter.captureMessage(ERROR_MESSAGE, SentryReporterLevel.ERROR)

        assertTrue(delegate.messages.isEmpty())
    }

    @Test
    fun suppresses_exception_when_opted_out() {
        val pref = FakeOptOutPreference(optedOut = true)
        val delegate = RecordingSentryReporter()
        val reporter = OptOutAwareSentryReporter(delegate, pref)

        reporter.captureException(RuntimeException("boom"))

        assertTrue(delegate.exceptions.isEmpty())
    }

    @Test
    fun runtime_pref_flip_takes_effect_immediately() {
        val pref = FakeOptOutPreference(optedOut = false)
        val delegate = RecordingSentryReporter()
        val reporter = OptOutAwareSentryReporter(delegate, pref)

        reporter.captureException(RuntimeException("first"))
        pref.setOptedOut(true)
        reporter.captureException(RuntimeException("second"))
        pref.setOptedOut(false)
        reporter.captureException(RuntimeException("third"))

        assertEquals(2, delegate.exceptions.size)
        assertEquals("first", delegate.exceptions[0].message)
        assertEquals("third", delegate.exceptions[1].message)
    }

    private class FakeOptOutPreference(private var optedOut: Boolean) : TelemetryOptOutPreference {
        override fun isOptedOut(): Boolean = optedOut
        override fun setOptedOut(optedOut: Boolean) {
            this.optedOut = optedOut
        }
    }

    private class RecordingSentryReporter : SentryReporter {
        val breadcrumbs: MutableList<RecordedBreadcrumb> = mutableListOf()
        val messages: MutableList<RecordedMessage> = mutableListOf()
        val exceptions: MutableList<Throwable> = mutableListOf()

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
