package com.lap.mobile.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.lap.mobile.R
import com.lap.mobile.dashboard.BackInterceptPolicy
import com.lap.mobile.dashboard.RecordingStripState
import com.lap.mobile.ui.theme.LapTheme

/**
 * Back-gesture intercept handler for the dashboard during a
 * recording session (T204) — pops a confirm dialog instead of
 * letting the system back gesture silently dismiss the screen.
 *
 * Why this guard matters: drivers triggering the system back
 * gesture mid-corner is far more common than tapping Stop —
 * edge-swipe targets are huge, accidental triggers rampant.
 * Without this handler, a driver who rests a thumb on the
 * screen edge mid-session would silently dismiss the dashboard
 * with no warning, lose visibility of the active session, and
 * panic ("did I stop recording??") even though the foreground
 * service is still capturing.
 *
 * Behavior driven by [BackInterceptPolicy.shouldIntercept]:
 *   - [RecordingStripState.Idle]      → BackHandler disabled,
 *     back gesture passes through to normal nav (close
 *     dashboard / pop back-stack).
 *   - [RecordingStripState.Recording] → BackHandler enabled,
 *     gesture is intercepted and the confirm dialog appears.
 *
 * The dialog body is the load-bearing piece of UX information:
 * "Recording continues in the background." Without that line,
 * users tap Cancel out of fear of losing the session. With it,
 * they make an informed choice — same reason "you have unsaved
 * changes" dialogs explain the consequence rather than just
 * asking yes/no.
 *
 * Stateless by callback — caller wires up [onConfirmExit] which
 * fires only after the dialog's confirm button is tapped. The
 * handler never navigates itself; it only signals confirmed
 * intent up to the caller (typically the dashboard ViewModel
 * or a nav controller).
 *
 * Why a separate composable rather than inline in the dashboard
 * scaffold: keeping intercept logic in a dedicated leaf makes
 * the contract testable (kernel = JVM-tested), reusable (any
 * future "discard unsaved data" flow can use the same shape),
 * and keeps the dashboard scaffold uncluttered with dialog state.
 *
 * @Preview fixture covers the dialog visual (idle/recording
 * branches don't render anything visible — Idle returns nothing,
 * Recording-without-back-press shows nothing until the user
 * triggers back).
 *
 * Live device verification — actual back-gesture interception,
 * predictive-back animation behavior, dialog interaction via
 * touch + TalkBack — is [PENDING: device-required]. Previews
 * lock the dialog visual; [BackInterceptPolicy]'s JVM tests
 * lock the intercept truth table.
 */
@Composable
fun RecordingBackInterceptHandler(state: RecordingStripState, onConfirmExit: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    BackHandler(enabled = BackInterceptPolicy.shouldIntercept(state = state)) {
        showDialog = true
    }
    if (showDialog) {
        ExitConfirmDialog(
            onConfirm = {
                showDialog = false
                onConfirmExit()
            },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun ExitConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.exit_confirm_title)) },
        text = { Text(text = stringResource(R.string.exit_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.exit_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.exit_confirm_dismiss))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun ExitConfirmDialogPreview() {
    LapTheme {
        ExitConfirmDialog(onConfirm = { }, onDismiss = { })
    }
}
