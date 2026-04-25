package com.lap.mobile.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lap.mobile.R
import com.lap.mobile.dashboard.LapTimeFormatter
import com.lap.mobile.ui.theme.LapTheme

private val ContainerPadding = 16.dp
private val NumberToTimerSpacing = 12.dp
private val LabelToNumberSpacing = 4.dp

private const val PREVIEW_LIVE_LAP: Int = 3
private const val PREVIEW_LIVE_ELAPSED_MS: Long = 87_415L

/**
 * Lap counter + current-lap timer display (T194).
 *
 * Stateless leaf — caller passes [currentLapNumber] (Int) and
 * [currentLapElapsedMs] (Long) directly from
 * [com.lap.mobile.dashboard.DashboardUiState]. The timer string is
 * derived from [LapTimeFormatter.format] so the JVM-tested kernel is
 * the source of truth for the M:SS.mmm convention; the display
 * Composable just renders the resolved string.
 *
 * Visual layout: a Row with two columns:
 *   - Left: small "Lap" label above a large lap-number digit
 *     (titleLarge typography, brand-color emphasis).
 *   - Right: the formatted timer in displayLarge — the dominant
 *     element because mid-lap drivers care about "how much time
 *     until I'm slow" more than "which lap is this".
 *
 * Why no progress ring or animation: lap-time is a fast-changing
 * Long. Animating digit transitions would alias-flicker at 10Hz
 * updates and obscure the actual value. A static text update is
 * what every motorsport timing display does for the same reason.
 *
 * Why two side-by-side blocks (not stacked vertically): on a
 * dashboard mount the user's eye scans horizontally; lap-number on
 * the left + timer on the right matches the standard live-timing
 * layout (sector splits flow the same way). T191's Figma mockup
 * may refine the proportions but the side-by-side anchor is the
 * convention.
 *
 * @Preview fixtures cover idle (lap 0, 0:00.000) and live (lap 3,
 * 1:27.415 — same values as T192's DashboardScreenLivePreview so
 * comparing previews shows the visual evolution).
 *
 * Live device verification (the layout reads correctly on real
 * hardware at racing-mount distance, no glyph-shift jitter as the
 * milliseconds tick) is [PENDING: device-required]. The previews
 * lock the visual contract.
 */
@Composable
fun LapDisplay(currentLapNumber: Int, currentLapElapsedMs: Long, modifier: Modifier = Modifier) {
    Surface(modifier = modifier) {
        Row(
            modifier = Modifier.padding(ContainerPadding),
            horizontalArrangement = Arrangement.spacedBy(NumberToTimerSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(LabelToNumberSpacing),
            ) {
                Text(
                    text = stringResource(R.string.lap_display_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.lap_display_number_format, currentLapNumber),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = LapTimeFormatter.format(currentLapElapsedMs),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LapDisplayIdlePreview() {
    LapTheme {
        LapDisplay(currentLapNumber = 0, currentLapElapsedMs = 0L)
    }
}

@Preview(showBackground = true)
@Composable
private fun LapDisplayLivePreview() {
    LapTheme {
        LapDisplay(currentLapNumber = PREVIEW_LIVE_LAP, currentLapElapsedMs = PREVIEW_LIVE_ELAPSED_MS)
    }
}
