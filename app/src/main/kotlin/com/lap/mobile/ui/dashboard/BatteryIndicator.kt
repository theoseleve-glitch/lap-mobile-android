package com.lap.mobile.ui.dashboard

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.lap.mobile.dashboard.BatteryStatus
import com.lap.mobile.dashboard.BatteryStatusClassifier
import com.lap.mobile.ui.theme.LapTheme

private val PillCornerRadius = 12.dp
private val PillHorizontalPadding = 10.dp
private val PillVerticalPadding = 4.dp

private const val PERCENT_MIN: Int = 0
private const val PERCENT_MAX: Int = 100

/**
 * Battery indicator (T201) — small "X%" pill that turns into a
 * red-tinted "Battery X% · low" pill when the percentage drops
 * below 20%.
 *
 * Stateless leaf — caller passes the raw [percentage] (0-100,
 * with defensive clamping); classification + visual flip is
 * delegated to [BatteryStatusClassifier].
 *
 * Why text changes (not just color) in the warning state: drivers
 * focused mid-corner are unlikely to notice a pill changing from
 * surfaceVariant to errorContainer in the corner of their eye.
 * The label change "73%" → "Battery 19% · low" makes the warning
 * legible at a passing glance and gives screen-readers something
 * meaningful to announce. Color carries the alarm; text carries
 * the explanation.
 *
 * Same pill shape as T181/T198/T199/T200 — visual consistency lets
 * the eye triangulate "this is a status pill" without parsing
 * each one.
 *
 * Color choice mirrors T198 GpsFixQualityIndicator's NoFix /
 * T200 RaceboxLinkIndicator's Disconnected (both use
 * errorContainer for "fix it" states), so all three "something
 * needs your attention" signals look visually parallel — driver
 * scanning the dashboard sees one consistent warning palette.
 *
 * The percentage is clamped to [0, 100] for display only — the
 * classifier sees the raw value (so a -1 "unknown" still
 * collapses to LowWarning per the kernel's defensive contract).
 *
 * @Preview fixtures cover full charge, mid charge, just-below-
 * threshold (19% warning), and dead (0% warning).
 *
 * Live device verification — actual rendering on real hardware,
 * indicator updates as the device drains and charges, BatteryManager
 * Intent.ACTION_BATTERY_CHANGED wiring — is [PENDING:
 * device-required]. The previews lock the visual contract and
 * the JVM tests lock the threshold logic.
 */
@Composable
fun BatteryIndicator(percentage: Int, modifier: Modifier = Modifier) {
    val status = BatteryStatusClassifier.classify(percentage)
    val displayPercent = percentage.coerceIn(PERCENT_MIN, PERCENT_MAX)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(PillCornerRadius),
        color = status.containerColor(),
        contentColor = status.contentColor(),
    ) {
        Text(
            modifier = Modifier.padding(
                horizontal = PillHorizontalPadding,
                vertical = PillVerticalPadding,
            ),
            text = status.label(displayPercent),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun BatteryStatus.label(percentage: Int): String = when (this) {
    BatteryStatus.Normal -> stringResource(R.string.battery_indicator_format, percentage)
    BatteryStatus.LowWarning -> stringResource(R.string.battery_indicator_low_format, percentage)
}

@Composable
private fun BatteryStatus.containerColor(): Color = when (this) {
    BatteryStatus.Normal -> MaterialTheme.colorScheme.surfaceVariant
    BatteryStatus.LowWarning -> MaterialTheme.colorScheme.errorContainer
}

@Composable
private fun BatteryStatus.contentColor(): Color = when (this) {
    BatteryStatus.Normal -> MaterialTheme.colorScheme.onSurfaceVariant
    BatteryStatus.LowWarning -> MaterialTheme.colorScheme.onErrorContainer
}

@Preview(showBackground = true)
@Composable
private fun BatteryIndicatorFullPreview() {
    LapTheme {
        BatteryIndicator(percentage = 100)
    }
}

@Preview(showBackground = true)
@Composable
private fun BatteryIndicatorMidPreview() {
    LapTheme {
        BatteryIndicator(percentage = 73)
    }
}

@Preview(showBackground = true)
@Composable
private fun BatteryIndicatorLowWarningPreview() {
    LapTheme {
        BatteryIndicator(percentage = 19)
    }
}

@Preview(showBackground = true)
@Composable
private fun BatteryIndicatorDeadPreview() {
    LapTheme {
        BatteryIndicator(percentage = 3)
    }
}
