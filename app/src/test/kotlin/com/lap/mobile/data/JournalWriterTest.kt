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
        val writer = JournalWriter(dao, clock, heartbeatInterval = Duration.ofSeconds(5))

        val job = writer.start(
            scope = backgroundScope,
            sessionId = sessionId,
            fileOffsetSource = { 0L },
        )
        // First tick fires immediately…
        runCurrent()
        assertEquals(1, dao.upsertCalls)

        // …then one tick per 5 virtual seconds.
        repeat(5) {
            clock.advance(Duration.ofSeconds(5))
            advanceTimeBy(5_000)
            runCurrent()
        }
        assertEquals(6, dao.upsertCalls)
        job.cancel()
    }

    @Test
    fun persists_current_file_offset_on_each_beat() = runTest {
        val dao = FakeJournalDao()
        val clock = ScheduledClock(startAt = Instant.parse("2026-04-24T09:00:00Z"))
        val writer = JournalWriter(dao, clock, heartbeatInterval = Duration.ofSeconds(5))

        var offset = 0L
        val job = writer.start(
            scope = backgroundScope,
            sessionId = sessionId,
            fileOffsetSource = { offset },
        )
        runCurrent()
        assertEquals(0L, dao.rows.getValue(sessionId).fileOffset)

        offset = 2_048L
        clock.advance(Duration.ofSeconds(5))
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(2_048L, dao.rows.getValue(sessionId).fileOffset)

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
        val writer = JournalWriter(dao, clock, heartbeatInterval = Duration.ofSeconds(5))

        val job = writer.start(
            scope = backgroundScope,
            sessionId = sessionId,
            fileOffsetSource = { 0L },
        )
        runCurrent()
        assertEquals(start, dao.rows.getValue(sessionId).lastHeartbeatAt)

        clock.advance(Duration.ofSeconds(5))
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(start.plusSeconds(5), dao.rows.getValue(sessionId).lastHeartbeatAt)

        job.cancel()
    }

    @Test
    fun cancel_stops_further_writes() = runTest {
        val dao = FakeJournalDao()
        val clock = ScheduledClock(startAt = Instant.parse("2026-04-24T09:00:00Z"))
        val writer = JournalWriter(dao, clock, heartbeatInterval = Duration.ofSeconds(5))

        val job = writer.start(
            scope = backgroundScope,
            sessionId = sessionId,
            fileOffsetSource = { 0L },
        )
        runCurrent()
        val beforeCancel = dao.upsertCalls
        job.cancel()

        clock.advance(Duration.ofMinutes(1))
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(beforeCancel, dao.upsertCalls)
        assertNotNull("row remains after cancel", dao.rows[sessionId])
    }
}

/** Test double for [JournalDao] — in-memory, records call count. */
private class FakeJournalDao : JournalDao {
    val rows: MutableMap<UUID7, SessionJournalEntity> = linkedMapOf()
    var upsertCalls: Int = 0
        private set

    override suspend fun upsertHeartbeat(entry: SessionJournalEntity) {
        rows[entry.sessionId] = entry
        upsertCalls++
    }

    override suspend fun listStale(before: Instant): List<SessionJournalEntity> =
        rows.values.filter { it.lastHeartbeatAt.isBefore(before) }

    override suspend fun findBySessionId(sessionId: UUID7): SessionJournalEntity? = rows[sessionId]
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
