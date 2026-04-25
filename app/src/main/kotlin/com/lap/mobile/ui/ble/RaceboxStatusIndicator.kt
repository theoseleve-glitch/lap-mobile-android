package com.lap.mobile.ui.ble

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lap.mobile.R
import com.lap.mobile.capture.ble.BleConnectionState
import com.lap.mobile.ui.theme.LapTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val PillCornerRadius = 12.dp
private val PillHorizontalPadding = 10.dp
private val PillVerticalPadding = 4.dp
private val PillRowSpacing = 8.dp
private val DotSize = 8.dp

/**
 * Dashboard-header RaceBox connection status pill (T130). Stateless
 * leaf — caller hoists the [com.lap.mobile.capture.ble.BleConnectionState]
 * via `viewModel.bleState.collectAsState()` and passes the enum
 * value. The reactive overload below wraps the same component for
 * direct StateFlow binding.
 *
 * Maps state → variant via the JVM-tested [RaceboxStatusDisplay]
 * kernel; the variant chooses a colour + label from the theme so the
 * pill stays readable in both light and dark modes.
 *
 * Compose preview gates the layout at assembleDebug time; live
 * dashboard integration is `[PENDING: device-required]`.
 */
@Composable
fun RaceboxStatusIndicator(state: BleConnectionState, modifier: Modifier = Modifier) {
    val variant = RaceboxStatusDisplay.variantFor(state)
    val containerColor = variant.containerColor()
    val contentColor = variant.contentColor()
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
                text = stringResource(state.labelRes()),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
fun RaceboxStatusIndicator(state: StateFlow<BleConnectionState>, modifier: Modifier = Modifier) {
    val current by state.collectAsState()
    RaceboxStatusIndicator(state = current, modifier = modifier)
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

private fun BleConnectionState.labelRes(): Int = when (this) {
    BleConnectionState.DISCONNECTED -> R.string.racebox_status_disconnected
    BleConnectionState.SCANNING -> R.string.racebox_status_scanning
    BleConnectionState.CONNECTING -> R.string.racebox_status_connecting
    BleConnectionState.CONNECTED -> R.string.racebox_status_connected
    BleConnectionState.LOST -> R.string.racebox_status_lost
}

@Composable
private fun RaceboxStatusVariant.containerColor(): Color = when (this) {
    RaceboxStatusVariant.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
    RaceboxStatusVariant.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
    RaceboxStatusVariant.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
    RaceboxStatusVariant.WARNING -> MaterialTheme.colorScheme.errorContainer
}

@Composable
private fun RaceboxStatusVariant.contentColor(): Color = when (this) {
    RaceboxStatusVariant.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    RaceboxStatusVariant.ACTIVE -> MaterialTheme.colorScheme.onPrimaryContainer
    RaceboxStatusVariant.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
    RaceboxStatusVariant.WARNING -> MaterialTheme.colorScheme.onErrorContainer
}

@Preview(showBackground = true)
@Composable
private fun RaceboxStatusIndicatorDisconnectedPreview() {
    LapTheme {
        RaceboxStatusIndicator(state = BleConnectionState.DISCONNECTED)
    }
}

@Preview(showBackground = true)
@Composable
private fun RaceboxStatusIndicatorScanningPreview() {
    LapTheme {
        RaceboxStatusIndicator(state = BleConnectionState.SCANNING)
    }
}

@Preview(showBackground = true)
@Composable
private fun RaceboxStatusIndicatorConnectingPreview() {
    LapTheme {
        RaceboxStatusIndicator(state = BleConnectionState.CONNECTING)
    }
}

@Preview(showBackground = true)
@Composable
private fun RaceboxStatusIndicatorConnectedPreview() {
    LapTheme {
        RaceboxStatusIndicator(state = BleConnectionState.CONNECTED)
    }
}

@Preview(showBackground = true)
@Composable
private fun RaceboxStatusIndicatorLostPreview() {
    LapTheme {
        RaceboxStatusIndicator(state = BleConnectionState.LOST)
    }
}

@Preview(showBackground = true)
@Composable
private fun RaceboxStatusIndicatorReactivePreview() {
    LapTheme {
        RaceboxStatusIndicator(state = MutableStateFlow(BleConnectionState.CONNECTED))
    }
}
