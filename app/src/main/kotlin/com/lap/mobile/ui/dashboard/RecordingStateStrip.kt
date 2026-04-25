package com.lap.mobile.ui.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lap.mobile.R
import com.lap.mobile.dashboard.RecordingStripState
import com.lap.mobile.dashboard.SessionElapsedFormatter
import com.lap.mobile.ui.theme.LapTheme

private val StripHorizontalPadding = 16.dp
private val StripVerticalPadding = 8.dp

/**
 * Persistent recording-state strip (T202) — a full-width band
 * across the top of the dashboard that answers a single driver
 * question at all times: "am I capturing right now?".
 *
 * Stateless leaf — caller passes the [state] from the
 * DashboardViewModel (Idle when no foreground capture service is
 * running, Recording(elapsedMs) when one is). The strip never
 * starts/stops capture; it only reflects the kernel's state.
 *
 * Why a strip and not another inline pill: the per-widget
 * indicators (T198 GPS, T199 sample-rate, T200 RaceBox, T201
 * battery) answer "is this measurement healthy?" — they're
 * scoped to one data source. The recording state is the dashboard-
 * wide lifecycle question, and answering it inline would force the
 * eye to hunt. A persistent full-width strip can never be missed.
 *
 * Two visual modes:
 *   - [RecordingStripState.Idle]      → muted surfaceVariant strip,
 *     label "Not recording" — matches the per-widget pills' default
 *     so an idle dashboard reads visually quiet.
 *   - [RecordingStripState.Recording] → primaryContainer strip with
 *     "● RECORDING · M:SS-or-H:MM:SS" label. Bullet glyph (●) is
 *     the universal voice-memo / camera "rec is on" convention so
 *     even a glance with no parsing recognizes the state.
 *
 * Why primaryContainer (not errorContainer) for Recording: the
 * dashboard reserves errorContainer for "fix it" warnings (T198
 * NoFix, T200 Disconnected, T201 LowWarning). A persistent red
 * strip would prime drivers to scan for problems instead of
 * confirming capture is active. Recording is the brand "happy
 * path" state — primaryContainer says "this is what the app is
 * for, and it's working."
 *
 * Why color AND text both change (not just one): color-blind users
 * can't rely on the color flip; the "● RECORDING" / "Not
 * recording" word swap carries the message regardless of palette
 * perception. Same color-plus-text reinforcement T200/T201 use.
 *
 * The elapsed-time string is computed by [SessionElapsedFormatter]
 * (T202 kernel — pure JVM-tested function locking the M:SS-under-
 * 1-hour / H:MM:SS-at-or-above-1-hour auto-switch).
 *
 * @Preview fixtures cover idle, short-session (recording 0:42),
 * and long-session (recording 1:23:45) so the auto-switch boundary
 * is visually verified at design time.
 *
 * Live device verification — actual rendering across configuration
 * changes, strip survives recomposition during long sessions, and
 * VM ↔ ForegroundService state propagation — is [PENDING:
 * device-required]. Previews lock the visual contract; the
 * formatter's JVM tests lock the timer-string contract.
 */
@Composable
fun RecordingStateStrip(state: RecordingStripState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = state.containerColor(),
        contentColor = state.contentColor(),
    ) {
        Text(
            modifier = Modifier.padding(
                horizontal = StripHorizontalPadding,
                vertical = StripVerticalPadding,
            ),
            text = state.label(),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun RecordingStripState.label(): String = when (this) {
    RecordingStripState.Idle -> stringResource(R.string.recording_strip_idle)
    is RecordingStripState.Recording -> stringResource(
        R.string.recording_strip_active_format,
        SessionElapsedFormatter.format(sessionElapsedMs),
    )
}

@Composable
private fun RecordingStripState.containerColor(): Color = when (this) {
    RecordingStripState.Idle -> MaterialTheme.colorScheme.surfaceVariant
    is RecordingStripState.Recording -> MaterialTheme.colorScheme.primaryContainer
}

@Composable
private fun RecordingStripState.contentColor(): Color = when (this) {
    RecordingStripState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
    is RecordingStripState.Recording -> MaterialTheme.colorScheme.onPrimaryContainer
}

@Preview(showBackground = true)
@Composable
private fun RecordingStateStripIdlePreview() {
    LapTheme {
        RecordingStateStrip(state = RecordingStripState.Idle)
    }
}

@Preview(showBackground = true)
@Composable
private fun RecordingStateStripShortSessionPreview() {
    LapTheme {
        RecordingStateStrip(state = RecordingStripState.Recording(sessionElapsedMs = 42_000L))
    }
}

@Preview(showBackground = true)
@Composable
private fun RecordingStateStripLongSessionPreview() {
    LapTheme {
        // 1h 23m 45s — locks the H:MM:SS auto-switch at preview time.
        val ms = 3_600_000L + 23 * 60_000L + 45 * 1_000L
        RecordingStateStrip(state = RecordingStripState.Recording(sessionElapsedMs = ms))
    }
}
