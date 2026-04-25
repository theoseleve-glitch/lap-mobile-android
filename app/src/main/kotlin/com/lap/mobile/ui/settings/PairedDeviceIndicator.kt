package com.lap.mobile.ui.settings

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
import com.lap.mobile.auth.PairedDeviceState
import com.lap.mobile.ui.theme.LapTheme

private val PillCornerRadius = 12.dp
private val PillHorizontalPadding = 10.dp
private val PillVerticalPadding = 4.dp
private val PillRowSpacing = 8.dp
private val DotSize = 8.dp

private const val PREVIEW_IDENTIFIER: String = "driver@example.com"

/**
 * Settings paired-device indicator pill (T181). Stateless leaf —
 * caller derives the [PairedDeviceState] from
 * [com.lap.mobile.auth.PairedDeviceStateResolver] (which reads
 * [com.lap.mobile.auth.SecureTokenStorage]) and passes it in. The
 * "Settings host screen" doesn't exist as a Composable yet (no task
 * has carved it out), but this leaf is reusable wherever a one-line
 * pairing-status glance is wanted.
 *
 * Visual shape mirrors [com.lap.mobile.ui.ble.RaceboxStatusIndicator]
 * — same pill + colored dot + label structure — so a user looking at
 * the dashboard or settings recognizes "this is a status pill" no
 * matter which subsystem it came from.
 *
 * The label flexes when [PairedDeviceState.Paired.identifier] is
 * non-null: "Paired · driver@example.com" rather than just "Paired".
 * Until T189's `/me` profile sync lands the identifier is null, so
 * the indicator just shows "Paired".
 */
@Composable
fun PairedDeviceIndicator(state: PairedDeviceState, modifier: Modifier = Modifier) {
    val containerColor = state.containerColor()
    val contentColor = state.contentColor()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(PillCornerRadius),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier =
            Modifier.padding(
                horizontal = PillHorizontalPadding,
                vertical = PillVerticalPadding,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PillRowSpacing),
        ) {
            StatusDot(color = contentColor)
            Text(
                text = state.label(),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Surface(
        modifier = Modifier.size(DotSize).clip(CircleShape).background(color),
        shape = CircleShape,
        color = color,
        content = { },
    )
}

@Composable
private fun PairedDeviceState.label(): String = when (this) {
    PairedDeviceState.NotPaired -> stringResource(R.string.paired_device_status_not_paired)
    is PairedDeviceState.Paired ->
        if (identifier == null) {
            stringResource(R.string.paired_device_status_paired)
        } else {
            stringResource(R.string.paired_device_status_paired_with_identifier, identifier)
        }
}

@Composable
private fun PairedDeviceState.containerColor(): Color = when (this) {
    PairedDeviceState.NotPaired -> MaterialTheme.colorScheme.surfaceVariant
    is PairedDeviceState.Paired -> MaterialTheme.colorScheme.primaryContainer
}

@Composable
private fun PairedDeviceState.contentColor(): Color = when (this) {
    PairedDeviceState.NotPaired -> MaterialTheme.colorScheme.onSurfaceVariant
    is PairedDeviceState.Paired -> MaterialTheme.colorScheme.onPrimaryContainer
}

@Preview(showBackground = true)
@Composable
private fun PairedDeviceIndicatorNotPairedPreview() {
    LapTheme {
        PairedDeviceIndicator(state = PairedDeviceState.NotPaired)
    }
}

@Preview(showBackground = true)
@Composable
private fun PairedDeviceIndicatorPairedNoIdentifierPreview() {
    LapTheme {
        PairedDeviceIndicator(state = PairedDeviceState.Paired(identifier = null))
    }
}

@Preview(showBackground = true)
@Composable
private fun PairedDeviceIndicatorPairedWithIdentifierPreview() {
    LapTheme {
        PairedDeviceIndicator(state = PairedDeviceState.Paired(identifier = PREVIEW_IDENTIFIER))
    }
}
