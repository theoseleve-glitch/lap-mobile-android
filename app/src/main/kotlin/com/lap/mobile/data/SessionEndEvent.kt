package com.lap.mobile.data

import com.lap.schemas.v1.UUID7
import java.time.Instant

/**
 * Domain event emitted by [SessionCoordinator.stop] when a recording
 * transitions to FINALIZING (T078). Downstream listeners — the bundle
 * writer (T131) and any analytics pipeline — react to this event
 * rather than polling state, so the bundle build kicks off as soon as
 * the user taps Stop.
 *
 * Carries the post-stop snapshot ([endedAt] from the coordinator's
 * clock at emit time, [sampleCount] from the StateFlow at emit time)
 * so consumers don't need to re-query the coordinator.
 */
data class SessionEndEvent(
    val sessionId: UUID7,
    val endedAt: Instant,
    val sampleCount: Int,
)
