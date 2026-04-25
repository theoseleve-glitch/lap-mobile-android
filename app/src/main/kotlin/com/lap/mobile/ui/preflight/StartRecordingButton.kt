package com.lap.mobile.ui.preflight

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.lap.mobile.R
import com.lap.mobile.capture.BatteryLevelCheck
import com.lap.mobile.capture.GpsFixQualityCheck
import com.lap.mobile.capture.LocationPermissionCheck
import com.lap.mobile.capture.PreflightStatus
import com.lap.mobile.capture.canStart
import com.lap.mobile.ui.theme.LapTheme

/**
 * Start-recording button gated by preflight (T097). Disabled exactly
 * when [PreflightStatus.canStart] returns `false` — i.e., any
 * hard-block reason present. Soft warnings (battery, thermal) leave
 * the button enabled because the user implicitly acknowledges the
 * trade-off by tapping Start.
 *
 * The decision lives in [canStart] so it's JVM-testable without a
 * Compose runtime; this composable is a thin presentation layer
 * over that boolean. Visual disabled-state is on-device verifiable
 * via the previews + the recording-screen instrumented test (T083,
 * `[PENDING: device-required]`).
 */
@Composable
fun StartRecordingButton(status: PreflightStatus, onStart: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onStart,
        enabled = status.canStart(),
        modifier = modifier,
    ) {
        Text(stringResource(R.string.preflight_start_button))
    }
}

@Preview(showBackground = true)
@Composable
private fun StartRecordingButtonReadyPreview() {
    LapTheme {
        StartRecordingButton(status = PreflightStatus.Ready, onStart = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun StartRecordingButtonSoftWarnPreview() {
    LapTheme {
        StartRecordingButton(
            status = PreflightStatus.NotReady(reasons = listOf(BatteryLevelCheck.REASON_BATTERY_LOW)),
            onStart = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StartRecordingButtonHardBlockPreview() {
    LapTheme {
        StartRecordingButton(
            status = PreflightStatus.NotReady(
                reasons = listOf(
                    LocationPermissionCheck.REASON_LOCATION_PERMISSION,
                    GpsFixQualityCheck.REASON_GPS_NO_FIX,
                ),
            ),
            onStart = {},
        )
    }
}
