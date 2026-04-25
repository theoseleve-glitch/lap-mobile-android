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
import com.lap.mobile.dashboard.GpsFixQuality
import com.lap.mobile.ui.theme.LapTheme

private val PillCornerRadius = 12.dp
private val PillHorizontalPadding = 10.dp
private val PillVerticalPadding = 4.dp
private val PillRowSpacing = 8.dp
private val DotSize = 8.dp

/**
 * GPS fix-quality status pill (T198) — red/yellow/green dot + label
 * communicating whether the dashboard's live data is trustworthy.
 *
 * Stateless leaf — caller resolves the [quality] from
 * [com.lap.mobile.dashboard.GpsFixQualityClassifier] and passes it
 * in. Same shape as T181's [PairedDeviceIndicator] (colored dot +
 * label inside rounded surface) so a driver looking at the
 * dashboard recognizes "this is a status pill" no matter which
 * subsystem it came from.
 *
 * Color choices map to MaterialTheme.colorScheme variants — Good
 * uses primary (the "everything works" brand color), Marginal uses
 * tertiary (the "noticeable but not critical" accent), NoFix uses
 * error (the unmistakable "something's wrong" signal). All three
 * flow through dark mode + dynamic-color (Material You)
 * automatically.
 *
 * Label changes alongside the color so the message is readable
 * even for color-blind users: "GPS" / "GPS · weak" / "No GPS".
 *
 * @Preview fixtures cover all three states.
 *
 * Live device verification — actual rendering on real hardware,
 * dot reads at glance distance, indicator updates as GPS state
 * changes — is [PENDING: device-required]. The previews lock the
 * visual contract.
 */
@Composable
fun GpsFixQualityIndicator(quality: GpsFixQuality, modifier: Modifier = Modifier) {
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
private fun GpsFixQuality.label(): String = when (this) {
    GpsFixQuality.Good -> stringResource(R.string.gps_fix_quality_good)
    GpsFixQuality.Marginal -> stringResource(R.string.gps_fix_quality_marginal)
    GpsFixQuality.NoFix -> stringResource(R.string.gps_fix_quality_no_fix)
}

@Composable
private fun GpsFixQuality.containerColor(): Color = when (this) {
    GpsFixQuality.Good -> MaterialTheme.colorScheme.primaryContainer
    GpsFixQuality.Marginal -> MaterialTheme.colorScheme.tertiaryContainer
    GpsFixQuality.NoFix -> MaterialTheme.colorScheme.errorContainer
}

@Composable
private fun GpsFixQuality.contentColor(): Color = when (this) {
    GpsFixQuality.Good -> MaterialTheme.colorScheme.onPrimaryContainer
    GpsFixQuality.Marginal -> MaterialTheme.colorScheme.onTertiaryContainer
    GpsFixQuality.NoFix -> MaterialTheme.colorScheme.onErrorContainer
}

@Preview(showBackground = true)
@Composable
private fun GpsFixQualityIndicatorGoodPreview() {
    LapTheme {
        GpsFixQualityIndicator(quality = GpsFixQuality.Good)
    }
}

@Preview(showBackground = true)
@Composable
private fun GpsFixQualityIndicatorMarginalPreview() {
    LapTheme {
        GpsFixQualityIndicator(quality = GpsFixQuality.Marginal)
    }
}

@Preview(showBackground = true)
@Composable
private fun GpsFixQualityIndicatorNoFixPreview() {
    LapTheme {
        GpsFixQualityIndicator(quality = GpsFixQuality.NoFix)
    }
}
