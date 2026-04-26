package com.lap.mobile.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lap.mobile.R
import com.lap.mobile.dashboard.DashboardUiState
import com.lap.mobile.dashboard.RecordingStripState
import com.lap.mobile.ui.theme.LapTheme

private val ScreenPadding = 24.dp
private val FieldSpacing = 16.dp

private const val PREVIEW_LIVE_SPEED_KMH: Double = 142.0
private const val PREVIEW_LIVE_LAP: Int = 3
private const val PREVIEW_LIVE_ELAPSED_MS: Long = 87_415L

/**
 * Live-dashboard screen scaffold (T192).
 *
 * Stateless leaf — caller observes
 * [com.lap.mobile.dashboard.DashboardViewModel.state] (typically via
 * `collectAsStateWithLifecycle`) and passes the [DashboardUiState]
 * in. Same shape as T180's [com.lap.mobile.ui.auth.ManualPairingCodeScreen]
 * and T181's [com.lap.mobile.ui.settings.PairedDeviceIndicator] —
 * stateless screen + ViewModel-side state holder, separated by a
 * StateFlow seam.
 *
 * The current rendering is a placeholder Column of Text rows, NOT
 * the final dashboard layout. T191's Figma mockup [BLOCKED:
 * design-task-requires-figma in this loop runner] will define the
 * actual visual hierarchy; T193 (speed gauge), T194 (lap counter),
 * T195 (track map) replace these placeholders piece-by-piece.
 *
 * Why the placeholder Column matters even though it's temporary:
 * the @Preview compiles, the route can be wired into nav graph, and
 * the StateFlow→UiState→Composable plumbing is exercised end-to-end
 * before any specific gauge/counter/map work begins. T193+ get to
 * focus on rendering, not scaffolding.
 *
 * `@Preview` fixtures cover idle (default zeros) and live (mid-
 * session sample values) — both compile-checked at assembleDebug
 * the same way T181's PairedDeviceIndicator previews are.
 */
@Composable
fun DashboardScreen(state: DashboardUiState, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(FieldSpacing),
        ) {
            Text(
                text = stringResource(R.string.dashboard_speed_kmh, state.currentSpeedKmh.toString()),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.dashboard_lap_number, state.currentLapNumber),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.dashboard_lap_elapsed_ms, state.currentLapElapsedMs),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text =
                if (state.recordingStripState is RecordingStripState.Recording) {
                    stringResource(R.string.dashboard_status_recording)
                } else {
                    stringResource(R.string.dashboard_status_idle)
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardScreenIdlePreview() {
    LapTheme {
        DashboardScreen(state = DashboardUiState())
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardScreenLivePreview() {
    LapTheme {
        DashboardScreen(
            state = DashboardUiState(
                currentSpeedKmh = PREVIEW_LIVE_SPEED_KMH,
                currentLapNumber = PREVIEW_LIVE_LAP,
                currentLapElapsedMs = PREVIEW_LIVE_ELAPSED_MS,
                recordingStripState = RecordingStripState.Recording(sessionElapsedMs = PREVIEW_LIVE_ELAPSED_MS),
            ),
        )
    }
}
