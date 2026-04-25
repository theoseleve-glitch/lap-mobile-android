package com.lap.mobile.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks T192's "compiles" acceptance for [DashboardViewModel] +
 * [DashboardUiState] — minimal scaffold so T193 (speed gauge), T194
 * (lap counter), T195 (track map) can plug into a stable VM contract.
 *
 * Defaults matter at the scaffold layer: the dashboard renders BEFORE
 * any data has arrived (cold-launch, first frame, screen-rotation
 * recreation). A driver opening the app pre-session sees the gauge
 * at 0, lap counter at 0, elapsed time at 0 — not "—" placeholders or
 * a loading spinner. Locking those defaults here means future tasks
 * adding fields (T195's GPS trail) can pick their own defaults
 * without breaking the cold-launch contract.
 *
 * No methods on the VM yet — those land with T196+ when there's a
 * sensor/state-source to wire. Adding them speculatively now would be
 * YAGNI.
 *
 * The test is constructor-free DI on purpose: T192 acceptance is
 * "compiles", not "Hilt graph wired". @HiltViewModel + @Inject
 * constructor are present so the Hilt graph compiles, but the JVM
 * test instantiates directly to keep the gate cheap.
 */
class DashboardViewModelTest {
    @Test
    fun initial_state_is_default_dashboard_ui_state_so_pre_session_dashboard_renders_zeroed_not_blank() {
        val viewModel = DashboardViewModel()

        assertEquals(DashboardUiState(), viewModel.state.value)
    }

    @Test
    fun default_speed_is_zero_kmh_so_cold_launch_gauge_reads_zero_not_undefined() {
        val state = DashboardUiState()

        assertEquals(0.0, state.currentSpeedKmh, 0.0001)
    }

    @Test
    fun default_lap_number_is_zero_so_cold_launch_counter_reads_lap_zero_pre_first_lap() {
        val state = DashboardUiState()

        assertEquals(0, state.currentLapNumber)
    }

    @Test
    fun default_lap_elapsed_is_zero_ms_so_pre_session_timer_reads_00_00_000_not_dashed() {
        val state = DashboardUiState()

        assertEquals(0L, state.currentLapElapsedMs)
    }

    @Test
    fun default_is_not_recording_so_cold_launch_dashboard_shows_idle_state_not_a_phantom_active_session() {
        // The dashboard reuses across "pre-session", "live recording",
        // "post-session review". The default isRecording=false makes
        // sure cold-launch can't wrongly imply we're capturing data
        // — important because a dashboard claiming "RECORDING" without
        // an actual capture pipeline would be a UX lie.
        val state = DashboardUiState()

        assertEquals(false, state.isRecording)
    }
}
