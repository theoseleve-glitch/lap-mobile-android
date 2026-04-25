package com.lap.mobile.ui.preflight

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lap.mobile.R
import com.lap.mobile.capture.PreflightStatus
import com.lap.mobile.ui.theme.LapTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

private val OuterPadding = 16.dp
private val RowSpacing = 8.dp

/**
 * Live-updating preflight checklist (T096). Stateless leaf — caller
 * hoists the [PreflightStatus] (typically by collecting the
 * [com.lap.mobile.capture.PreflightService]'s polled status flow,
 * which T099 lands at 2 s cadence) and passes it in.
 *
 * Reasons render as raw codes for now; per-item human-readable
 * labels + remediation CTAs land in T098. The reactive overload
 * mirrors the [com.lap.mobile.ui.recording.SampleCountBadge] pattern
 * — same StateFlow / Flow seam so screens can pick whichever shape
 * fits their state holder.
 *
 * Compose preview gates the layout at assembleDebug; live render is
 * `[PENDING: device-required]` for the recording-screen
 * instrumented test once Block J's wiring lands.
 */
@Composable
fun PreflightChecklist(status: PreflightStatus, modifier: Modifier = Modifier) {
    Surface(modifier = modifier) {
        Column(modifier = Modifier.padding(OuterPadding)) {
            Text(
                text = stringResource(R.string.preflight_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(RowSpacing))
            when (status) {
                is PreflightStatus.Ready -> ReadyRow()
                is PreflightStatus.NotReady -> NotReadyRows(reasons = status.reasons)
            }
        }
    }
}

/**
 * Reactive overload — collects [statusFlow] into Compose state and
 * forwards. Lets ViewModels expose a `Flow<PreflightStatus>` from
 * [com.lap.mobile.capture.PreflightService] without an intermediate
 * `collectAsState` at every call site.
 */
@Composable
fun PreflightChecklist(statusFlow: Flow<PreflightStatus>, modifier: Modifier = Modifier) {
    val status by statusFlow.collectAsState(initial = PreflightStatus.NotReady(reasons = emptyList()))
    PreflightChecklist(status = status, modifier = modifier)
}

@Composable
private fun ReadyRow() {
    Text(
        text = stringResource(R.string.preflight_status_ready),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun NotReadyRows(reasons: List<String>) {
    Column {
        Text(
            text = stringResource(R.string.preflight_status_not_ready_count, reasons.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(RowSpacing))
        reasons.forEach { reason ->
            Text(
                text = stringResource(R.string.preflight_reason_label, reason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = RowSpacing / 2),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreflightChecklistReadyPreview() {
    LapTheme {
        PreflightChecklist(status = PreflightStatus.Ready)
    }
}

@Preview(showBackground = true)
@Composable
private fun PreflightChecklistOneFailurePreview() {
    LapTheme {
        PreflightChecklist(
            status = PreflightStatus.NotReady(reasons = listOf("location_permission")),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreflightChecklistManyFailuresPreview() {
    LapTheme {
        PreflightChecklist(
            status = PreflightStatus.NotReady(
                reasons = listOf(
                    "location_permission",
                    "notification_permission",
                    "gps_no_fix",
                    "track_not_selected",
                    "battery_low",
                ),
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreflightChecklistReactivePreview() {
    LapTheme {
        PreflightChecklist(
            statusFlow = MutableStateFlow(
                PreflightStatus.NotReady(reasons = listOf("gps_no_fix", "battery_low")),
            ),
        )
    }
}
