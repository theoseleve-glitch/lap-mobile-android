package com.lap.mobile.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lap.mobile.R
import com.lap.mobile.dashboard.RaceboxLinkQuality
import com.lap.mobile.ui.theme.LapTheme

private val PillCornerRadius = 12.dp
private val PillHorizontalPadding = 10.dp
private val PillVerticalPadding = 4.dp
private val PillRowSpacing = 8.dp
private val DotSize = 8.dp

/**
 * RaceBox BLE-link status pill (T200) — red/yellow/green dot +
 * label communicating whether the dashboard's RaceBox-sourced live
 * data is trustworthy.
 *
 * Stateless leaf — caller resolves the [quality] from
 * [com.lap.mobile.dashboard.RaceboxLinkQualityClassifier] (which
 * collapses [com.lap.mobile.capture.ble.BleConnectionState]'s
 * 5-state machine into 3 visual bands) and passes it in.
 *
 * Same pill shape as T181 PairedDeviceIndicator + T198
 * GpsFixQualityIndicator + T199 SampleRateIndicator — a driver
 * looking at the dashboard recognizes "this is a status pill" no
 * matter which subsystem it came from. Container/content colors
 * flex via theme variants, identical scheme to T198's GPS
 * indicator (so RaceBox + GPS have visually-parallel "data source
 * health" semantics):
 *   - Connected    → primaryContainer  (brand "everything works")
 *   - Pending      → tertiaryContainer (accent "wait briefly")
 *   - Disconnected → errorContainer    (unmistakable "fix it")
 *
 * Label changes alongside color so the message is readable for
 * color-blind users: "RaceBox" / "RaceBox · linking" /
 * "No RaceBox".
 *
 * @Preview fixtures cover all three states.
 *
 * Live device verification — actual rendering on real hardware,
 * indicator updates as BLE state machine cycles through scan →
 * connect → connected → lost → reconnect — is [PENDING:
 * device-required]. The previews lock the visual contract.
 */
@Composable
fun RaceboxLinkIndicator(quality: RaceboxLinkQuality, modifier: Modifier = Modifier) {
    val containerColor = quality.containerColor()
    val contentColor = quality.contentColor()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(PillCornerRadius),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = PillHorizontalPadding,
                vertical = PillVerticalPadding,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PillRowSpacing),
        ) {
            StatusDot(color = contentColor)
            Text(
                text = quality.label(),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Surface(
        modifier = Modifier
            .size(DotSize)
            .clip(CircleShape)
            .background(color),
        shape = CircleShape,
        color = color,
        content = { },
    )
}

@Composable
private fun RaceboxLinkQuality.label(): String = when (this) {
    RaceboxLinkQuality.Connected -> stringResource(R.string.racebox_link_connected)
    RaceboxLinkQuality.Pending -> stringResource(R.string.racebox_link_pending)
    RaceboxLinkQuality.Disconnected -> stringResource(R.string.racebox_link_disconnected)
}

@Composable
private fun RaceboxLinkQuality.containerColor(): Color = when (this) {
    RaceboxLinkQuality.Connected -> MaterialTheme.colorScheme.primaryContainer
    RaceboxLinkQuality.Pending -> MaterialTheme.colorScheme.tertiaryContainer
    RaceboxLinkQuality.Disconnected -> MaterialTheme.colorScheme.errorContainer
}

@Composable
private fun RaceboxLinkQuality.contentColor(): Color = when (this) {
    RaceboxLinkQuality.Connected -> MaterialTheme.colorScheme.onPrimaryContainer
    RaceboxLinkQuality.Pending -> MaterialTheme.colorScheme.onTertiaryContainer
    RaceboxLinkQuality.Disconnected -> MaterialTheme.colorScheme.onErrorContainer
}

@Preview(showBackground = true)
@Composable
private fun RaceboxLinkIndicatorConnectedPreview() {
    LapTheme {
        RaceboxLinkIndicator(quality = RaceboxLinkQuality.Connected)
    }
}

@Preview(showBackground = true)
@Composable
private fun RaceboxLinkIndicatorPendingPreview() {
    LapTheme {
        RaceboxLinkIndicator(quality = RaceboxLinkQuality.Pending)
    }
}

@Preview(showBackground = true)
@Composable
private fun RaceboxLinkIndicatorDisconnectedPreview() {
    LapTheme {
        RaceboxLinkIndicator(quality = RaceboxLinkQuality.Disconnected)
    }
}
