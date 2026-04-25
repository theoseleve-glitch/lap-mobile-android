package com.lap.mobile.capture

import com.lap.mobile.data.RecordingState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EXPECTED_ONE_ACQUIRE: Int = 1
private const val EXPECTED_ONE_RELEASE: Int = 1
private const val EXPECTED_TWO_ACQUIRES: Int = 2
private const val EXPECTED_TWO_RELEASES: Int = 2

/**
 * Unit tests for [RecordingPowerGuard] (T081). Drives the guard via a
 * [MutableStateFlow] of [RecordingState] and asserts that the
 * [PowerLock] is acquired exactly while the recording is in
 * `RECORDING`, released on every other state, and never leaked when
 * the consuming coroutine is cancelled.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordingPowerGuardTest {
    @Test
    fun idle_state_does_not_acquire_lock() = runTest {
        val lock = FakePowerLock()
        val state = MutableStateFlow(RecordingState.IDLE)
        val guard = RecordingPowerGuard(lock, state)

        val job = guard.start(backgroundScope)
        runCurrent()

        assertFalse("IDLE must not hold the lock", lock.isHeld)
        assertEquals(0, lock.acquireCount)
        job.cancelAndJoin()
    }

    @Test
    fun armed_state_does_not_acquire_lock() = runTest {
        val lock = FakePowerLock()
        val state = MutableStateFlow(RecordingState.ARMED)
        val guard = RecordingPowerGuard(lock, state)

        val job = guard.start(backgroundScope)
        runCurrent()

        assertFalse("ARMED must not hold the lock — wait until RECORDING", lock.isHeld)
        job.cancelAndJoin()
    }

    @Test
    fun transition_to_recording_acquires_lock_once() = runTest {
        val lock = FakePowerLock()
        val state = MutableStateFlow(RecordingState.IDLE)
        val guard = RecordingPowerGuard(lock, state)
        val job = guard.start(backgroundScope)

        state.value = RecordingState.ARMED
        state.value = RecordingState.RECORDING
        runCurrent()

        assertTrue("RECORDING must hold the lock", lock.isHeld)
        assertEquals(EXPECTED_ONE_ACQUIRE, lock.acquireCount)
        job.cancelAndJoin()
    }

    @Test
    fun transition_away_from_recording_releases_lock() = runTest {
        val lock = FakePowerLock()
        val state = MutableStateFlow(RecordingState.IDLE)
        val guard = RecordingPowerGuard(lock, state)
        val job = guard.start(backgroundScope)
        state.value = RecordingState.ARMED
        state.value = RecordingState.RECORDING
        runCurrent()

        state.value = RecordingState.FINALIZING
        runCurrent()

        assertFalse("FINALIZING must release the lock", lock.isHeld)
        assertEquals(EXPECTED_ONE_RELEASE, lock.releaseCount)
        job.cancelAndJoin()
    }

    @Test
    fun re_entry_into_recording_re_acquires_lock() = runTest {
        val lock = FakePowerLock()
        val state = MutableStateFlow(RecordingState.IDLE)
        val guard = RecordingPowerGuard(lock, state)
        val job = guard.start(backgroundScope)

        state.value = RecordingState.ARMED
        state.value = RecordingState.RECORDING
        runCurrent()
        state.value = RecordingState.FINALIZING
        runCurrent()
        state.value = RecordingState.IDLE
        runCurrent()
        state.value = RecordingState.ARMED
        state.value = RecordingState.RECORDING
        runCurrent()

        assertTrue("second RECORDING window must hold lock again", lock.isHeld)
        assertEquals(EXPECTED_TWO_ACQUIRES, lock.acquireCount)
        assertEquals(EXPECTED_ONE_RELEASE, lock.releaseCount)
        job.cancelAndJoin()
    }

    @Test
    fun cancel_while_holding_releases_lock_in_finally() = runTest {
        val lock = FakePowerLock()
        val state = MutableStateFlow(RecordingState.RECORDING)
        val guard = RecordingPowerGuard(lock, state)
        val job = guard.start(backgroundScope)
        runCurrent()
        assertTrue("precondition: lock should be held while in RECORDING", lock.isHeld)

        job.cancelAndJoin()

        assertFalse("cancellation MUST release the lock — no OS-level leaks", lock.isHeld)
    }

    @Test
    fun completed_state_releases_lock_like_finalizing() = runTest {
        val lock = FakePowerLock()
        val state = MutableStateFlow(RecordingState.RECORDING)
        val guard = RecordingPowerGuard(lock, state)
        val job = guard.start(backgroundScope)
        runCurrent()

        state.value = RecordingState.COMPLETED
        runCurrent()

        assertFalse(lock.isHeld)
        job.cancelAndJoin()
    }

    @Test
    fun failed_state_releases_lock_like_finalizing() = runTest {
        val lock = FakePowerLock()
        val state = MutableStateFlow(RecordingState.RECORDING)
        val guard = RecordingPowerGuard(lock, state)
        val job = guard.start(backgroundScope)
        runCurrent()

        state.value = RecordingState.FAILED
        runCurrent()

        assertFalse(lock.isHeld)
        job.cancelAndJoin()
    }

    @Test
    fun two_recording_sessions_acquire_and_release_twice() = runTest {
        val lock = FakePowerLock()
        val state = MutableStateFlow(RecordingState.IDLE)
        val guard = RecordingPowerGuard(lock, state)
        val job = guard.start(backgroundScope)

        state.value = RecordingState.RECORDING
        runCurrent()
        state.value = RecordingState.FINALIZING
        runCurrent()
        state.value = RecordingState.IDLE
        runCurrent()
        state.value = RecordingState.RECORDING
        runCurrent()
        state.value = RecordingState.COMPLETED
        runCurrent()

        assertEquals(EXPECTED_TWO_ACQUIRES, lock.acquireCount)
        assertEquals(EXPECTED_TWO_RELEASES, lock.releaseCount)
        job.cancelAndJoin()
    }
}
