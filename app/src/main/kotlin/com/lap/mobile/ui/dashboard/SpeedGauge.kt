package com.lap.mobile.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lap.mobile.R
import com.lap.mobile.dashboard.AccessibilitySpeechValues
import com.lap.mobile.dashboard.SpeedZone
import com.lap.mobile.dashboard.SpeedZoneClassifier
import com.lap.mobile.ui.theme.LapTheme
import kotlin.math.roundToInt

private val GaugeSize = 220.dp
private val GaugeStrokeWidth = 8.dp
private const val GAUGE_FILL_ALPHA: Float = 0.15f

private const val PREVIEW_COOL_KMH: Double = 12.0
private const val PREVIEW_CRUISE_KMH: Double = 78.0
private const val PREVIEW_FAST_KMH: Double = 124.0
private const val PREVIEW_REDLINE_KMH: Double = 187.0

/**
 * Large, glanceable, color-coded speed gauge (T193).
 *
 * Stateless leaf — caller passes the current [speedKmh] value
 * (typically from
 * [com.lap.mobile.dashboard.DashboardUiState.currentSpeedKmh]). The
 * gauge's color band is derived from [SpeedZoneClassifier.classify]
 * so the JVM-tested kernel is the source of truth for "what counts
 * as Fast"; the gauge itself just renders the resolved zone.
 *
 * Visual: a 220dp circular ring with a tinted fill and a thick
 * border in the zone's accent color, with the integer-rounded speed
 * in `displayLarge` typography centered inside, and "km/h" as a
 * `labelMedium` unit beneath. Glanceable from arm's length on a
 * dashboard mount; the integer-rounded speed avoids the ".0" /
 * decimal-jitter that makes a live-updating Double hard to read.
 *
 * Color choices map to MaterialTheme.colorScheme variants rather
 * than hardcoded colors so dark mode + dynamic-color (Material You)
 * theming flows through automatically:
 *   - [SpeedZone.Cool]    → outline (calm gray)
 *   - [SpeedZone.Cruise]  → primary (brand color, "we're moving")
 *   - [SpeedZone.Fast]    → tertiary (accent, "active racing")
 *   - [SpeedZone.Redline] → error (alert red, "near limits")
 *
 * @Preview fixtures cover all four zones at sample speeds. T191's
 * Figma mockup will likely refine the visual hierarchy when it
 * lands; the kernel mapping (speed→zone) and the
 * surface-of-Composable-takes-Double-renders-zone-color are stable.
 *
 * Live device verification (the gauge looks right on real hardware,
 * 10Hz speed updates don't tear the ring) is [PENDING:
 * device-required]. The previews lock the visual contract.
 */
@Composable
fun SpeedGauge(speedKmh: Double, modifier: Modifier = Modifier) {
    val zone = SpeedZoneClassifier.classify(speedKmh)
    val zoneColor = zone.accentColor()
    val announcement = stringResource(
        R.string.accessibility_speed_format,
        AccessibilitySpeechValues.roundSpeedForAnnouncement(speedKmh = speedKmh),
    )
    Box(
        modifier = modifier
            .size(GaugeSize)
            .clip(CircleShape)
            .background(zoneColor.copy(alpha = GAUGE_FILL_ALPHA))
            .border(GaugeStrokeWidth, zoneColor, CircleShape)
            .semantics { contentDescription = announcement },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = speedKmh.coerceAtLeast(0.0).roundToInt().toString(),
                style = MaterialTheme.typography.displayLarge,
                color = zoneColor,
            )
            Text(
                text = stringResource(R.string.speed_gauge_unit_kmh),
                style = MaterialTheme.typography.labelMedium,
                color = zoneColor,
            )
        }
    }
}

@Composable
private fun SpeedZone.accentColor(): Color = when (this) {
    SpeedZone.Cool -> MaterialTheme.colorScheme.outline
    SpeedZone.Cruise -> MaterialTheme.colorScheme.primary
    SpeedZone.Fast -> MaterialTheme.colorScheme.tertiary
    SpeedZone.Redline -> MaterialTheme.colorScheme.error
}

@Preview(showBackground = true)
@Composable
private fun SpeedGaugeCoolPreview() {
    LapTheme {
        SpeedGauge(speedKmh = PREVIEW_COOL_KMH)
    }
}

@Preview(showBackground = true)
@Composable
private fun SpeedGaugeCruisePreview() {
    LapTheme {
        SpeedGauge(speedKmh = PREVIEW_CRUISE_KMH)
    }
}

@Preview(showBackground = true)
@Composable
private fun SpeedGaugeFastPreview() {
    LapTheme {
        SpeedGauge(speedKmh = PREVIEW_FAST_KMH)
    }
}

@Preview(showBackground = true)
@Composable
private fun SpeedGaugeRedlinePreview() {
    LapTheme {
        SpeedGauge(speedKmh = PREVIEW_REDLINE_KMH)
    }
}
