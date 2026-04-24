package com.lap.mobile.capture

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

private const val STALE_THRESHOLD_SECONDS: Long = 5L
private const val STALE_THRESHOLD_MILLIS: Long = STALE_THRESHOLD_SECONDS * 1_000L
private const val UNDER_THRESHOLD_MILLIS: Long = 3_000L
private const val WELL_OVER_THRESHOLD_MILLIS: Long = 6_000L

private const val LATITUDE: Double = 47.643_222
private const val LONGITUDE: Double = 6.026_018
private const val ACCURACY_M: Float = 3.5f
private const val SOURCE_TS_NS_A: Long = 1_000L
private const val SOURCE_TS_NS_B: Long = 2_000L
private const val RECEIPT_MS_A: Long = 10L
private const val RECEIPT_MS_B: Long = 20L

/**
 * Virtual-time tests for [LocationFixMonitor]. The state-machine
 * contract under test (T067):
 *
 *  - initial state is [LocationFixState.Searching]
 *  - first sample → [LocationFixState.Acquired]
 *  - no sample within `staleThreshold` → [LocationFixState.Lost]
 *  - a new sample within the threshold resets the watchdog (no Lost)
 *  - a new sample after Lost recovers to Acquired
 *
 * Tests use `runTest`'s virtual time so a 6-second stale threshold
 * elapses in nanoseconds of wall time — same scheduler as T054
 * JournalWriterTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocationFixMonitorTest {
    private val sampleA: LocationSample = locationSample(SOURCE_TS_NS_A, RECEIPT_MS_A)
    private val sampleB: LocationSample = locationSample(SOURCE_TS_NS_B, RECEIPT_MS_B)

    @Test
    fun initial_state_is_searching_with_no_samples() = runTest {
        val monitor = LocationFixMonitor(Duration.ofSeconds(STALE_THRESHOLD_SECONDS))
        val states = mutableListOf<LocationFixState>()
        val job = launch { monitor.observe(emptyFlow()).toList(states) }

        runCurrent()
        // emptyFlow() completes immediately, so observe() finishes after emitting Searching.
        job.cancel()

        assertEquals(listOf(LocationFixState.Searching), states)
    }

    @Test
    fun first_sample_transitions_to_acquired() = runTest {
        val source = MutableSharedFlow<LocationSample>(extraBufferCapacity = 8)
        val monitor = LocationFixMonitor(Duration.ofSeconds(STALE_THRESHOLD_SECONDS))
        val states = mutableListOf<LocationFixState>()
        val job = launch { monitor.observe(source).toList(states) }
        runCurrent()

        source.emit(sampleA)
        runCurrent()

        assertEquals(
            listOf(LocationFixState.Searching, LocationFixState.Acquired),
            states,
        )
        job.cancel()
    }

    @Test
    fun no_sample_within_stale_threshold_transitions_to_lost() = runTest {
        val source = MutableSharedFlow<LocationSample>(extraBufferCapacity = 8)
        val monitor = LocationFixMonitor(Duration.ofSeconds(STALE_THRESHOLD_SECONDS))
        val states = mutableListOf<LocationFixState>()
        val job = launch { monitor.observe(source).toList(states) }
        runCurrent()

        source.emit(sampleA)
        runCurrent()
        advanceTimeBy(WELL_OVER_THRESHOLD_MILLIS)
        runCurrent()

        assertEquals(
            listOf(LocationFixState.Searching, LocationFixState.Acquired, LocationFixState.Lost),
            states,
        )
        job.cancel()
    }

    @Test
    fun new_sample_within_threshold_does_not_emit_lost() = runTest {
        val source = MutableSharedFlow<LocationSample>(extraBufferCapacity = 8)
        val monitor = LocationFixMonitor(Duration.ofSeconds(STALE_THRESHOLD_SECONDS))
        val states = mutableListOf<LocationFixState>()
        val job = launch { monitor.observe(source).toList(states) }
        runCurrent()

        source.emit(sampleA)
        runCurrent()
        advanceTimeBy(UNDER_THRESHOLD_MILLIS)
        source.emit(sampleB)
        runCurrent()
        advanceTimeBy(UNDER_THRESHOLD_MILLIS)
        runCurrent()

        // Cumulative elapsed: 6s > threshold, but each sample reset the watchdog at 3s.
        assertEquals(
            listOf(LocationFixState.Searching, LocationFixState.Acquired, LocationFixState.Acquired),
            states,
        )
        job.cancel()
    }

    @Test
    fun sample_at_exact_threshold_boundary_does_not_emit_lost() = runTest {
        val source = MutableSharedFlow<LocationSample>(extraBufferCapacity = 8)
        val monitor = LocationFixMonitor(Duration.ofSeconds(STALE_THRESHOLD_SECONDS))
        val states = mutableListOf<LocationFixState>()
        val job = launch { monitor.observe(source).toList(states) }
        runCurrent()

        source.emit(sampleA)
        runCurrent()
        // Advance to exactly the threshold — Lost would fire at threshold + 1ms
        advanceTimeBy(STALE_THRESHOLD_MILLIS - 1L)
        source.emit(sampleB)
        runCurrent()

        // No Lost emission yet — boundary belongs to "still fresh".
        assertEquals(
            listOf(LocationFixState.Searching, LocationFixState.Acquired, LocationFixState.Acquired),
            states,
        )
        job.cancel()
    }

    @Test
    fun sample_after_lost_recovers_to_acquired() = runTest {
        val source = MutableSharedFlow<LocationSample>(extraBufferCapacity = 8)
        val monitor = LocationFixMonitor(Duration.ofSeconds(STALE_THRESHOLD_SECONDS))
        val states = mutableListOf<LocationFixState>()
        val job = launch { monitor.observe(source).toList(states) }
        runCurrent()

        source.emit(sampleA)
        runCurrent()
        advanceTimeBy(WELL_OVER_THRESHOLD_MILLIS)
        runCurrent()
        source.emit(sampleB)
        runCurrent()

        assertEquals(
            listOf(
                LocationFixState.Searching,
                LocationFixState.Acquired,
                LocationFixState.Lost,
                LocationFixState.Acquired,
            ),
            states,
        )
        job.cancel()
    }

    private fun locationSample(sourceNs: Long, receiptMs: Long): LocationSample = LocationSample(
        latitude = LATITUDE,
        longitude = LONGITUDE,
        accuracyMeters = ACCURACY_M,
        altitudeMeters = null,
        speedMps = null,
        bearingDeg = null,
        sourceTimestampNs = sourceNs,
        receiptUtcMillis = receiptMs,
    )
}
