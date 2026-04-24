package com.lap.mobile.data

import com.lap.schemas.v1.UUID7
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Bumps [SessionJournalEntity.lastHeartbeatAt] every
 * [heartbeatInterval] while a session is recording, so the startup
 * reconciler (T055) can detect crashed sessions via stale journal rows
 * and offer Finalize-from-journal recovery (ADR-012, T059).
 *
 * The writer does NOT own its CoroutineScope — the caller passes one
 * in via [start], typically a [CaptureForegroundService]'s lifecycle
 * scope (T063). This keeps the writer pure enough to drive from
 * `runTest` with virtual time: the injected [Clock] reads whatever the
 * test scheduler advances, and `delay` is free (no wall-clock waits).
 *
 * The first tick fires immediately so a journal row exists from
 * `t = 0` — a crash within the first 5 s still surfaces to the
 * reconciler. Subsequent ticks sleep [heartbeatInterval] between writes.
 */
class JournalWriter(
    private val dao: JournalDao,
    private val clock: Clock,
    private val heartbeatInterval: Duration = Duration.ofSeconds(5),
) {
    fun start(scope: CoroutineScope, sessionId: UUID7, fileOffsetSource: () -> Long): Job = scope.launch {
        while (isActive) {
            dao.upsertHeartbeat(
                SessionJournalEntity(
                    sessionId = sessionId,
                    lastHeartbeatAt = Instant.now(clock),
                    status = "recording",
                    fileOffset = fileOffsetSource(),
                ),
            )
            delay(heartbeatInterval.toMillis())
        }
    }
}
