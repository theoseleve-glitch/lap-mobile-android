package com.lap.mobile.ui.dashboard

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lap.mobile.R
import com.lap.mobile.ui.theme.LapTheme
import kotlin.math.roundToInt

private val PillCornerRadius = 12.dp
private val PillHorizontalPadding = 10.dp
private val PillVerticalPadding = 4.dp

/**
 * Sample-rate indicator (T199) — small "X Hz" pill showing the
 * achieved recording cadence.
 *
 * Stateless leaf — caller resolves Hz from
 * [com.lap.mobile.dashboard.SampleRateCalculator] over a sliding
 * window of recent sample timestamps and passes the [hz] value in.
 *
 * Why "achieved" Hz visible to the driver: a 10Hz-configured
 * pipeline that's only delivering 7Hz (BLE drops, thermal
 * throttling, sensor sleep) is a problem the driver should see —
 * they can decide whether to keep capturing or restart. Hiding
 * the number behind a "recording" label would let degradation pass
 * silently.
 *
 * Display rounds to integer Hz — sub-Hz precision in the rendered
 * number flickers at 10Hz updates and adds no actionable
 * information. The kernel returns a Double so a future
 * "10.5 Hz target" config can pass through cleanly; the renderer
 * rounds for display.
 *
 * Visual: same shape as T181 PairedDeviceIndicator + T198
 * GpsFixQualityIndicator (rounded surface pill, labelMedium text).
 * Color uses surfaceVariant — neutral, doesn't fight for attention
 * against the speed gauge / lap timer / status indicators. Sample
 * rate is informational, not status-critical.
 *
 * @Preview fixtures cover idle (0 Hz), typical 10 Hz GPS rate, and
 * 50 Hz IMU rate.
 *
 * Live device verification — actual rendering on real hardware,
 * indicator updates as pipeline cadence changes during BLE
 * disconnect / reconnect — is [PENDING: device-required]. The
 * previews lock the visual contract and the JVM tests lock the
 * Hz calculation.
 */
@Composable
fun SampleRateIndicator(hz: Double, modifier: Modifier = Modifier) {
    val displayHz = hz.coerceAtLeast(0.0).roundToInt()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(PillCornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            modifier = Modifier.padding(
                horizontal = PillHorizontalPadding,
                vertical = PillVerticalPadding,
            ),
            text = stringResource(R.string.sample_rate_format_hz, displayHz),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SampleRateIndicatorIdlePreview() {
    LapTheme {
        SampleRateIndicator(hz = 0.0)
    }
}

@Preview(showBackground = true)
@Composable
private fun SampleRateIndicatorGpsPreview() {
    LapTheme {
        SampleRateIndicator(hz = 10.0)
    }
}

@Preview(showBackground = true)
@Composable
private fun SampleRateIndicatorImuPreview() {
    LapTheme {
        SampleRateIndicator(hz = 50.0)
    }
}
