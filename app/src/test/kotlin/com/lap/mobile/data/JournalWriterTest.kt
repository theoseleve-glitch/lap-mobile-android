package com.lap.mobile.data

import com.lap.schemas.v1.UUID7
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

private const val INTERVAL_SECONDS: Long = 5L
private const val INTERVAL_MILLIS: Long = INTERVAL_SECONDS * 1000L
private const val EXPECTED_TOTAL_WRITES: Int = 6
private const val EXTRA_TICKS: Int = 5
private const val LATE_OFFSET: Long = 2_048L
private const val MINUTE_MILLIS: Long = 60_000L

/**
 * Virtual-time tests for [JournalWriter]. runTest's TestScope advances
 * `delay` without real wall-clock waits, so 30 minutes of heartbeats
 * runs in milliseconds. The [Clock] is driven off the same scheduler
 * via a small bridge so wall-clock reads line up with virtual time —
 * exactly what T054's "fake clock" bar requires.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JournalWriterTest {
    private val sessionId: UUID7 = "0194aaaa-aaaa-7aaa-aaaa-aaaaaaaaaaaa"

    @Test
    fun writes_heartbeat_every_configured_interval() = runTest {
        val dao = FakeJournalDao()
        val clock = ScheduledClock(startAt = Instant.parse("2026-04-24T09:00:00Z"))
        val writer = JournalWriter(dao, clock, heartbeatInterval = Duration.ofSeconds(INTERVAL_SECONDS))

        val job = writer.start(
            scope = backgroundScope,
            sessionId = sessionId,
            fileOffsetSource = { 0L },
        )
        // First tick fires immediately…
        runCurrent()
        assertEquals(1, dao.upsertCalls)

        // …then one tick per interval.
        repeat(EXTRA_TICKS) {
            clock.advance(Duration.ofSeconds(INTERVAL_SECONDS))
            advanceTimeBy(INTERVAL_MILLIS)
            runCurrent()
        }
        assertEquals(EXPECTED_TOTAL_WRITES, dao.upsertCalls)
        job.cancel()
    }

    @Test
    fun persists_current_file_offset_on_each_beat() = runTest {
        val dao = FakeJournalDao()
        val clock = ScheduledClock(startAt = Instant.parse("2026-04-24T09:00:00Z"))
        val writer = JournalWriter(dao, clock, heartbeatInterval = Duration.ofSeconds(INTERVAL_SECONDS))

        var offset = 0L
        val job = writer.start(
            scope = backgroundScope,
            sessionId = sessionId,
            fileOffsetSource = { offset },
        )
        runCurrent()
        assertEquals(0L, dao.rows.getValue(sessionId).fileOffset)

        offset = LATE_OFFSET
        clock.advance(Duration.ofSeconds(INTERVAL_SECONDS))
        advanceTimeBy(INTERVAL_MILLIS)
        runCurrent()
        assertEquals(LATE_OFFSET, dao.rows.getValue(sessionId).fileOffset)

        job.cancel()
    }

    @Test
    fun heartbeat_carries_recording_status_while_active() = runTest {
        val dao = FakeJournalDao()
        val clock = ScheduledClock(startAt = Instant.parse("2026-04-24T09:00:00Z"))
        val writer = JournalWriter(dao, clock)

        val job = writer.start(
            scope = backgroundScope,
            sessionId = sessionId,
            fileOffsetSource = { 0L },
        )
        runCurrent()
        assertEquals("recording", dao.rows.getValue(sessionId).status)
        job.cancel()
    }

    @Test
    fun heartbeat_timestamps_track_the_clock() = runTest {
        val start = Instant.parse("2026-04-24T09:00:00Z")
        val dao = FakeJournalDao()
        val clock = ScheduledClock(startAt = start)
        val writer = JournalWriter(dao, clock, heartbeatInterval = Duration.ofSeconds(INTERVAL_SECONDS))

        val job = writer.start(
            scope = backgroundScope,
            sessionId = sessionId,
            fileOffsetSource = { 0L },
        )
        runCurrent()
        assertEquals(start, dao.rows.getValue(sessionId).lastHeartbeatAt)

        clock.advance(Duration.ofSeconds(INTERVAL_SECONDS))
        advanceTimeBy(INTERVAL_MILLIS)
        runCurrent()
        assertEquals(start.plusSeconds(INTERVAL_SECONDS), dao.rows.getValue(sessionId).lastHeartbeatAt)

        job.cancel()
    }

    @Test
    fun cancel_stops_further_writes() = runTest {
        val dao = FakeJournalDao()
        val clock = ScheduledClock(startAt = Instant.parse("2026-04-24T09:00:00Z"))
        val writer = JournalWriter(dao, clock, heartbeatInterval = Duration.ofSeconds(INTERVAL_SECONDS))

        val job = writer.start(
            scope = backgroundScope,
            sessionId = sessionId,
            fileOffsetSource = { 0L },
        )
        runCurrent()
        val beforeCancel = dao.upsertCalls
        job.cancel()

        clock.advance(Duration.ofMinutes(1))
        advanceTimeBy(MINUTE_MILLIS)
        runCurrent()
        assertEquals(beforeCancel, dao.upsertCalls)
        assertNotNull("row remains after cancel", dao.rows[sessionId])
    }
}

/** Hand-advanced Clock so test virtual time and wall-clock reads stay aligned. */
private class ScheduledClock(startAt: Instant) : Clock() {
    private var now: Instant = startAt
    fun advance(by: Duration) {
        now = now.plus(by)
    }
    override fun instant(): Instant = now
    override fun getZone(): java.time.ZoneId = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId?): Clock = this
}
