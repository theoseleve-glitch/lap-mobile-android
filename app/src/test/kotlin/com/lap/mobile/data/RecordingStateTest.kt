package com.lap.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * State-machine contract for [RecordingState] (T075). Two layers:
 *
 *  1. **Wire values** mirror the magic strings the existing code uses
 *     in `SessionEntity.state` (raw strings until consumers migrate).
 *     Changing a wire value would break Room persistence.
 *  2. **Transitions** enumerate the valid moves through the recording
 *     lifecycle. The "recovery shortcut" RECORDING → COMPLETED is
 *     intentionally allowed because [FinalizeFromJournal] takes that
 *     path when finalizing a crashed session via journal replay
 *     (T059). Forbidding it would break existing behavior.
 *
 * Terminal states (COMPLETED, FAILED) cannot transition out — a new
 * session is required.
 */
class RecordingStateTest {
    @Test
    fun wire_values_match_existing_room_state_strings() {
        assertEquals("idle", RecordingState.IDLE.wireValue)
        assertEquals("armed", RecordingState.ARMED.wireValue)
        assertEquals("recording", RecordingState.RECORDING.wireValue)
        assertEquals("finalizing", RecordingState.FINALIZING.wireValue)
        assertEquals("completed", RecordingState.COMPLETED.wireValue)
        assertEquals("failed", RecordingState.FAILED.wireValue)
    }

    @Test
    fun fromWire_round_trips_every_state() {
        RecordingState.entries.forEach { state ->
            assertEquals(state, RecordingState.fromWire(state.wireValue))
        }
    }

    @Test
    fun fromWire_returns_null_on_unknown_string() {
        assertNull(RecordingState.fromWire("nonexistent"))
    }

    @Test
    fun fromWire_is_case_sensitive() {
        // Wire values are lowercase per the existing Room rows.
        assertNull(RecordingState.fromWire("RECORDING"))
        assertNull(RecordingState.fromWire("Recording"))
    }

    @Test
    fun idle_can_transition_to_armed_only() {
        assertCanTransition(RecordingState.IDLE, RecordingState.ARMED)
        assertCannotTransition(RecordingState.IDLE, RecordingState.RECORDING)
        assertCannotTransition(RecordingState.IDLE, RecordingState.FINALIZING)
        assertCannotTransition(RecordingState.IDLE, RecordingState.COMPLETED)
        assertCannotTransition(RecordingState.IDLE, RecordingState.FAILED)
    }

    @Test
    fun armed_can_cancel_back_to_idle_or_start_recording() {
        assertCanTransition(RecordingState.ARMED, RecordingState.IDLE)
        assertCanTransition(RecordingState.ARMED, RecordingState.RECORDING)
        assertCannotTransition(RecordingState.ARMED, RecordingState.FINALIZING)
        assertCannotTransition(RecordingState.ARMED, RecordingState.COMPLETED)
        assertCannotTransition(RecordingState.ARMED, RecordingState.FAILED)
    }

    @Test
    fun recording_can_finalize_or_jump_to_terminal_for_recovery() {
        // RECORDING → FINALIZING is the normal stop path.
        assertCanTransition(RecordingState.RECORDING, RecordingState.FINALIZING)
        // RECORDING → FAILED is the in-flight error path.
        assertCanTransition(RecordingState.RECORDING, RecordingState.FAILED)
        // RECORDING → COMPLETED is the recovery shortcut FinalizeFromJournal uses
        // when bringing a crashed session to terminal via journal replay.
        assertCanTransition(RecordingState.RECORDING, RecordingState.COMPLETED)
        // No going backwards.
        assertCannotTransition(RecordingState.RECORDING, RecordingState.IDLE)
        assertCannotTransition(RecordingState.RECORDING, RecordingState.ARMED)
    }

    @Test
    fun finalizing_can_only_terminate() {
        assertCanTransition(RecordingState.FINALIZING, RecordingState.COMPLETED)
        assertCanTransition(RecordingState.FINALIZING, RecordingState.FAILED)
        assertCannotTransition(RecordingState.FINALIZING, RecordingState.IDLE)
        assertCannotTransition(RecordingState.FINALIZING, RecordingState.ARMED)
        assertCannotTransition(RecordingState.FINALIZING, RecordingState.RECORDING)
    }

    @Test
    fun completed_is_terminal_with_no_outbound_transitions() {
        assertTrue(RecordingState.COMPLETED.isTerminal)
        RecordingState.entries.forEach { next ->
            assertCannotTransition(RecordingState.COMPLETED, next)
        }
    }

    @Test
    fun failed_is_terminal_with_no_outbound_transitions() {
        assertTrue(RecordingState.FAILED.isTerminal)
        RecordingState.entries.forEach { next ->
            assertCannotTransition(RecordingState.FAILED, next)
        }
    }

    @Test
    fun non_terminal_states_are_marked_as_such() {
        assertFalse(RecordingState.IDLE.isTerminal)
        assertFalse(RecordingState.ARMED.isTerminal)
        assertFalse(RecordingState.RECORDING.isTerminal)
        assertFalse(RecordingState.FINALIZING.isTerminal)
    }

    @Test
    fun no_state_can_transition_to_itself() {
        // Self-transitions would be a redundant write — the state machine
        // catches this so the upsert path doesn't quietly no-op.
        RecordingState.entries.forEach { state ->
            assertCannotTransition(state, state)
        }
    }

    private fun assertCanTransition(from: RecordingState, to: RecordingState) {
        assertTrue(
            "expected $from → $to to be allowed",
            from.canTransitionTo(to),
        )
    }

    private fun assertCannotTransition(from: RecordingState, to: RecordingState) {
        assertFalse(
            "expected $from → $to to be forbidden",
            from.canTransitionTo(to),
        )
    }
}
