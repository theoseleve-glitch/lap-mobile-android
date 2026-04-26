package com.lap.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for [OptOutAwareAnalyticsReporter] (T224).
 *
 * The wrapper gates every delegate call on a
 * [TelemetryOptOutPreference] check. Tests cover both sides of
 * the gate to lock the "respected" invariant — when the user
 * opts out, no event leaks through to the underlying
 * [AnalyticsReporter].
 */
class OptOutAwareAnalyticsReporterTest {
    @Test
    fun forwards_event_when_opted_in() {
        val pref = FakeOptOutPreference(optedOut = false)
        val delegate = RecordingAnalyticsReporter()
        val reporter = OptOutAwareAnalyticsReporter(delegate, pref)

        reporter.track(AnalyticsEvent.SESSION_STARTED, mapOf("session_id" to "x"))

        assertEquals(1, delegate.events.size)
        assertEquals(AnalyticsEvent.SESSION_STARTED, delegate.events.single().name)
        assertEquals("x", delegate.events.single().properties["session_id"])
    }

    @Test
    fun forwards_event_with_default_empty_properties_when_opted_in() {
        val pref = FakeOptOutPreference(optedOut = false)
        val delegate = RecordingAnalyticsReporter()
        val reporter = OptOutAwareAnalyticsReporter(delegate, pref)

        reporter.track(AnalyticsEvent.APP_STARTED)

        assertEquals(1, delegate.events.size)
        assertTrue(delegate.events.single().properties.isEmpty())
    }

    @Test
    fun suppresses_event_when_opted_out() {
        val pref = FakeOptOutPreference(optedOut = true)
        val delegate = RecordingAnalyticsReporter()
        val reporter = OptOutAwareAnalyticsReporter(delegate, pref)

        reporter.track(AnalyticsEvent.SESSION_STARTED, mapOf("session_id" to "x"))

        assertTrue(delegate.events.isEmpty())
    }

    @Test
    fun runtime_pref_flip_takes_effect_immediately() {
        val pref = FakeOptOutPreference(optedOut = false)
        val delegate = RecordingAnalyticsReporter()
        val reporter = OptOutAwareAnalyticsReporter(delegate, pref)

        reporter.track(AnalyticsEvent.APP_STARTED)
        pref.setOptedOut(true)
        reporter.track(AnalyticsEvent.SESSION_STARTED)
        pref.setOptedOut(false)
        reporter.track(AnalyticsEvent.SESSION_FINALIZED)

        assertEquals(2, delegate.events.size)
        assertEquals(AnalyticsEvent.APP_STARTED, delegate.events[0].name)
        assertEquals(AnalyticsEvent.SESSION_FINALIZED, delegate.events[1].name)
    }

    private class FakeOptOutPreference(private var optedOut: Boolean) : TelemetryOptOutPreference {
        override fun isOptedOut(): Boolean = optedOut
        override fun setOptedOut(optedOut: Boolean) {
            this.optedOut = optedOut
        }
    }

    private class RecordingAnalyticsReporter : AnalyticsReporter {
        val events: MutableList<RecordedEvent> = mutableListOf()

        override fun track(event: String, properties: Map<String, Any>) {
            events += RecordedEvent(event, properties)
        }
    }

    private data class RecordedEvent(val name: String, val properties: Map<String, Any>)
}
