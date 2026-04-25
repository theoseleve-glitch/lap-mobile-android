package com.lap.mobile.ui.dashboard

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lap.mobile.R
import com.lap.mobile.dashboard.StopHoldPolicy
import com.lap.mobile.ui.theme.LapTheme
import kotlinx.coroutines.delay

private val ButtonSize = 96.dp
private val ProgressStrokeWidth = 6.dp
private const val FRAME_INTERVAL_MS: Long = 16L
private const val PREVIEW_MID_HOLD_PROGRESS: Float = 0.5f

/**
 * Hold-to-stop button (T203) — the dashboard's destructive action,
 * guarded by two layers so accidental contact mid-session can't
 * end a recording.
 *
 * Two-layer safety design:
 *   1. **Hold ≥ 0.5s** — filters accidental contact (a brushed
 *      finger or a finger resting on the screen mid-corner). The
 *      activation threshold lives in [StopHoldPolicy] and is
 *      JVM-tested.
 *   2. **Confirm dialog after release** — filters intentional-but-
 *      mistaken hold (driver meant to tap a different control).
 *      Two layers ≈ vanishingly small accidental-stop probability.
 *
 * Visual feedback during hold: a circular progress ring fills from
 * 0% to 100% as the hold approaches the activation threshold. The
 * ring IS the activation signal — when it's full, the driver knows
 * "release now to confirm-stop" without parsing labels. Releasing
 * before the ring fills cancels the gesture silently (no dialog,
 * no state change).
 *
 * Why a hold instead of a guard-rail like a long-press menu or a
 * second-button-to-confirm: the dashboard real estate is already
 * dense, and a separate guard control would steal a tap target
 * the driver might need. A single button with hold-discipline + a
 * dialog adds zero footprint and zero new spatial memory.
 *
 * Why [SystemClock.uptimeMillis] instead of [System.currentTimeMillis]:
 * monotonic. Wall-clock time can jump backwards (NTP sync,
 * timezone change, manual clock set) which could make a hold
 * appear negative or zero-duration. uptimeMillis is monotonic
 * since boot — exactly what Android docs recommend for measuring
 * elapsed time within a single session.
 *
 * Stateless UI surface — caller passes [onStopConfirmed] which
 * fires only after the full two-layer gate (hold + confirm). The
 * button never starts or stops capture itself; it only signals
 * intent up to the ViewModel.
 *
 * @Preview fixtures cover idle, mid-hold (50% ring fill), and the
 * confirm dialog so the visual contract is locked at design time.
 *
 * Live device verification — actual touch gesture, ring animation
 * smoothness on real hardware, dialog interaction, accessibility
 * via TalkBack — is [PENDING: device-required]. Previews lock the
 * visual contract; [StopHoldPolicy]'s JVM tests lock the threshold
 * + progress arithmetic.
 */
@Composable
fun StopButton(onStopConfirmed: () -> Unit, modifier: Modifier = Modifier) {
    var pressStartUptimeMs by remember { mutableStateOf<Long?>(null) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    val contentDescriptionText = stringResource(R.string.stop_button_content_description)

    LaunchedEffect(pressStartUptimeMs) {
        val startedAt = pressStartUptimeMs ?: return@LaunchedEffect
        while (pressStartUptimeMs == startedAt) {
            val elapsed = SystemClock.uptimeMillis() - startedAt
            holdProgress = StopHoldPolicy.progress(holdDurationMs = elapsed)
            if (holdProgress >= 1f) break
            delay(FRAME_INTERVAL_MS)
        }
    }

    StopButtonSurface(
        progress = holdProgress,
        contentDescriptionText = contentDescriptionText,
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    val startedAt = SystemClock.uptimeMillis()
                    pressStartUptimeMs = startedAt
                    val released = tryAwaitRelease()
                    val elapsed = SystemClock.uptimeMillis() - startedAt
                    pressStartUptimeMs = null
                    holdProgress = 0f
                    if (released && StopHoldPolicy.wouldActivate(holdDurationMs = elapsed)) {
                        showConfirmDialog = true
                    }
                },
            )
        },
    )

    if (showConfirmDialog) {
        StopConfirmDialog(
            onConfirm = {
                showConfirmDialog = false
                onStopConfirmed()
            },
            onDismiss = { showConfirmDialog = false },
        )
    }
}

@Composable
private fun StopButtonSurface(progress: Float, contentDescriptionText: String, modifier: Modifier = Modifier) {
    val containerColor = MaterialTheme.colorScheme.errorContainer
    val accentColor = MaterialTheme.colorScheme.error
    val labelColor = MaterialTheme.colorScheme.onErrorContainer
    val strokeWidthPx = with(LocalDensity.current) { ProgressStrokeWidth.toPx() }
    Box(
        modifier = modifier
            .size(ButtonSize)
            .clip(CircleShape)
            .background(containerColor)
            .semantics { contentDescription = contentDescriptionText },
        contentAlignment = Alignment.Center,
    ) {
        if (progress > 0f) {
            HoldProgressRing(
                progress = progress,
                color = accentColor,
                strokeWidthPx = strokeWidthPx,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.stop_button_label),
                style = MaterialTheme.typography.titleMedium,
                color = labelColor,
            )
            if (progress > 0f) {
                Text(
                    text = stringResource(R.string.stop_button_hold_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                )
            }
        }
    }
}

@Composable
private fun HoldProgressRing(progress: Float, color: Color, strokeWidthPx: Float) {
    Canvas(modifier = Modifier.size(ButtonSize)) {
        val inset = strokeWidthPx / 2f
        val arcSize = Size(size.width - strokeWidthPx, size.height - strokeWidthPx)
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = strokeWidthPx),
        )
    }
}

@Composable
private fun StopConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.stop_confirm_title)) },
        text = { Text(text = stringResource(R.string.stop_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.stop_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.stop_confirm_dismiss))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun StopButtonIdlePreview() {
    LapTheme {
        StopButton(onStopConfirmed = { })
    }
}

@Preview(showBackground = true)
@Composable
private fun StopButtonMidHoldPreview() {
    // Static 50% progress fixture — exercises the ring-fill visual
    // path without requiring an actual press gesture in the preview.
    LapTheme {
        StopButtonSurface(
            progress = PREVIEW_MID_HOLD_PROGRESS,
            contentDescriptionText = "Stop recording. Hold for half a second.",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StopConfirmDialogPreview() {
    LapTheme {
        StopConfirmDialog(onConfirm = { }, onDismiss = { })
    }
}
