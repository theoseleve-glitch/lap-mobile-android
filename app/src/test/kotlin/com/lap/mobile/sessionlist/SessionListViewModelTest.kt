package com.lap.mobile.sessionlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ROW_ID_A = "0194aaaa-aaaa-7aaa-aaaa-aaaaaaaaaaaa"
private const val ROW_ID_B = "0194bbbb-bbbb-7bbb-bbbb-bbbbbbbbbbbb"
private const val SAMPLE_COUNT_A = 5_400
private const val SAMPLE_COUNT_B = 12_000
private const val ROW_STATE_FINAL = "FINALIZED"
private const val STARTED_AT_A = "2026-04-25T09:00:00Z"
private const val STARTED_AT_B = "2026-04-25T10:30:00Z"
private const val LOAD_ERROR_MESSAGE = "network unreachable"

/**
 * JVM unit tests for [SessionListViewModel] (T226).
 *
 * The VM is a minimal scaffold mirroring [com.lap.mobile.dashboard.DashboardViewModel]
 * — StateFlow<SessionListUiState> exposed via [SessionListViewModel.state],
 * three lifecycle methods (onLoadStarted / onSessionsLoaded / onLoadFailed)
 * the eventual repository wiring will drive when T227 lands.
 *
 * Tests lock the cold-launch contract (empty list, not loading, no error)
 * + each transition method's effect on the state record so a future
 * refactor that adds a field can't silently break the existing
 * pre-session contract that the eventual SessionListScreen will rely on.
 */
class SessionListViewModelTest {
    @Test
    fun initial_state_is_empty_list_not_loading_no_error() {
        val viewModel = SessionListViewModel()

        val state = viewModel.state.value
        assertTrue(state.sessions.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun on_load_started_flips_loading_true_and_clears_prior_error() {
        val viewModel = SessionListViewModel()
        viewModel.onLoadFailed(LOAD_ERROR_MESSAGE)

        viewModel.onLoadStarted()

        val state = viewModel.state.value
        assertTrue(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun on_sessions_loaded_replaces_list_clears_loading_clears_error() {
        val viewModel = SessionListViewModel()
        viewModel.onLoadStarted()
        viewModel.onLoadFailed(LOAD_ERROR_MESSAGE)
        viewModel.onLoadStarted()
        val rows = listOf(
            SessionListRow(
                id = ROW_ID_A,
                state = ROW_STATE_FINAL,
                sampleCount = SAMPLE_COUNT_A,
                startedAtIso = STARTED_AT_A,
            ),
            SessionListRow(
                id = ROW_ID_B,
                state = ROW_STATE_FINAL,
                sampleCount = SAMPLE_COUNT_B,
                startedAtIso = STARTED_AT_B,
            ),
        )

        viewModel.onSessionsLoaded(rows)

        val state = viewModel.state.value
        assertEquals(rows, state.sessions)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun on_sessions_loaded_with_empty_list_replaces_prior_rows() {
        val viewModel = SessionListViewModel()
        viewModel.onSessionsLoaded(
            listOf(SessionListRow(ROW_ID_A, ROW_STATE_FINAL, SAMPLE_COUNT_A, STARTED_AT_A)),
        )

        viewModel.onSessionsLoaded(emptyList())

        assertTrue(viewModel.state.value.sessions.isEmpty())
    }

    @Test
    fun on_load_failed_clears_loading_records_error_keeps_prior_rows() {
        val viewModel = SessionListViewModel()
        val priorRows = listOf(
            SessionListRow(ROW_ID_A, ROW_STATE_FINAL, SAMPLE_COUNT_A, STARTED_AT_A),
        )
        viewModel.onSessionsLoaded(priorRows)
        viewModel.onLoadStarted()

        viewModel.onLoadFailed(LOAD_ERROR_MESSAGE)

        val state = viewModel.state.value
        assertEquals(LOAD_ERROR_MESSAGE, state.errorMessage)
        assertFalse(state.isLoading)
        assertEquals(priorRows, state.sessions)
    }

    @Test
    fun row_with_null_started_at_is_supported_for_sessions_that_never_started_recording() {
        val viewModel = SessionListViewModel()
        val rows = listOf(
            SessionListRow(id = ROW_ID_A, state = "ARMED", sampleCount = 0, startedAtIso = null),
        )

        viewModel.onSessionsLoaded(rows)

        assertEquals(rows, viewModel.state.value.sessions)
        assertNull(viewModel.state.value.sessions.single().startedAtIso)
    }
}
