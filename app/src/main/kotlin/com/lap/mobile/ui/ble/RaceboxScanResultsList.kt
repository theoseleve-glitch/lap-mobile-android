package com.lap.mobile.ui.ble

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lap.mobile.R
import com.lap.mobile.capture.ble.RaceboxAdvertiser
import com.lap.mobile.ui.theme.LapTheme

private val RowVerticalPadding = 12.dp
private val RowHorizontalPadding = 16.dp
private val RowSpacing = 12.dp

private const val PREVIEW_RSSI_EXCELLENT: Int = -45
private const val PREVIEW_RSSI_GOOD: Int = -68
private const val PREVIEW_RSSI_FAIR: Int = -82
private const val PREVIEW_RSSI_POOR: Int = -101
private const val PREVIEW_ADDRESS_A: String = "AA:BB:CC:DD:EE:01"
private const val PREVIEW_ADDRESS_B: String = "AA:BB:CC:DD:EE:02"
private const val PREVIEW_ADDRESS_C: String = "AA:BB:CC:DD:EE:03"
private const val PREVIEW_ADDRESS_D: String = "AA:BB:CC:DD:EE:04"

/**
 * BLE scan-results picker (T123). Stateless leaf — caller passes the
 * [advertisers] list (sourced from [com.lap.mobile.capture.ble.RaceboxScanController.scanWithTimeout])
 * and an [onSelect] callback. Each row shows the human name (falling
 * back to MAC), the MAC, and a textual signal-strength label
 * computed by the JVM-tested [RssiSignalLevel.fromDbm] kernel.
 *
 * Compose preview gates the layout at assembleDebug time; live
 * on-device verification is `[PENDING: device-required]` for the
 * BLE scan instrumented test.
 */
@Composable
fun RaceboxScanResultsList(
    advertisers: List<RaceboxAdvertiser>,
    onSelect: (RaceboxAdvertiser) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (advertisers.isEmpty()) {
        Text(
            text = stringResource(R.string.ble_scan_results_empty),
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier.padding(RowHorizontalPadding),
        )
        return
    }
    LazyColumn(modifier = modifier) {
        items(items = advertisers, key = { it.address }) { advertiser ->
            RaceboxAdvertiserRow(advertiser = advertiser, onClick = { onSelect(advertiser) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun RaceboxAdvertiserRow(advertiser: RaceboxAdvertiser, onClick: () -> Unit) {
    val signalLevel = RssiSignalLevel.fromDbm(advertiser.rssiDbm)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowSpacing),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = advertiser.name ?: stringResource(R.string.ble_scan_unnamed_advertiser),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = advertiser.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(signalLevel.labelRes()),
                style = MaterialTheme.typography.labelMedium,
                color = signalLevel.color(),
            )
            Text(
                text = stringResource(R.string.ble_scan_rssi, advertiser.rssiDbm),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA),
            )
        }
    }
}

private const val SECONDARY_TEXT_ALPHA: Float = 0.6f

private fun RssiSignalLevel.labelRes(): Int = when (this) {
    RssiSignalLevel.EXCELLENT -> R.string.ble_signal_excellent
    RssiSignalLevel.GOOD -> R.string.ble_signal_good
    RssiSignalLevel.FAIR -> R.string.ble_signal_fair
    RssiSignalLevel.POOR -> R.string.ble_signal_poor
}

@Composable
private fun RssiSignalLevel.color() = when (this) {
    RssiSignalLevel.EXCELLENT, RssiSignalLevel.GOOD -> MaterialTheme.colorScheme.primary
    RssiSignalLevel.FAIR -> MaterialTheme.colorScheme.secondary
    RssiSignalLevel.POOR -> MaterialTheme.colorScheme.error
}

@Preview(showBackground = true)
@Composable
private fun RaceboxScanResultsListPopulatedPreview() {
    LapTheme {
        RaceboxScanResultsList(
            advertisers = listOf(
                RaceboxAdvertiser(PREVIEW_ADDRESS_A, "RaceBox Mini #1", PREVIEW_RSSI_EXCELLENT),
                RaceboxAdvertiser(PREVIEW_ADDRESS_B, "RaceBox Mini #2", PREVIEW_RSSI_GOOD),
                RaceboxAdvertiser(PREVIEW_ADDRESS_C, null, PREVIEW_RSSI_FAIR),
                RaceboxAdvertiser(PREVIEW_ADDRESS_D, "RaceBox far", PREVIEW_RSSI_POOR),
            ),
            onSelect = { },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RaceboxScanResultsListEmptyPreview() {
    LapTheme {
        RaceboxScanResultsList(advertisers = emptyList(), onSelect = { })
    }
}
