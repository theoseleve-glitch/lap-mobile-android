package com.lap.mobile.data

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Shared frozen-time [Clock] for unit tests that exercise time-windowed
 * logic ([JournalReconciler], [JournalRetention], …) without dragging
 * in `kotlinx.coroutines.test` virtual time. Promoted to `internal` so
 * sibling tests in the same package don't collide on `private class`.
 */
internal class FixedClock(private val now: Instant) : Clock() {
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
}
