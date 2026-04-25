package com.lap.mobile.ui.auth

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.lap.mobile.R
import com.lap.mobile.ui.theme.LapTheme

/**
 * Sign-in-again modal (T186). Stateless — caller decides when to
 * show it via the JVM-tested
 * [com.lap.mobile.auth.SessionExpiredDetectionPolicy], and supplies
 * the action callbacks.
 *
 * Body text is the user-visible "preserves local data" promise:
 * "Your local sessions and recordings are safe and will upload once
 * you sign in again." This isn't decorative — it tells the driver
 * who just finished an outing in the pit that the data they
 * captured is NOT gone, even though they've been bumped back to a
 * sign-in prompt. T186's whole UX exists because this reassurance
 * is what stops the user from panic-installing-again.
 *
 * Two actions: "Sign in again" (primary — routes to LoginScreen
 * T172) and "Later" (dismissive — closes the dialog so the user
 * can browse their local cache, e.g. review what's been captured,
 * before re-authenticating). The dismiss path doesn't re-arm the
 * modal; it stays dismissed for the rest of the session unless
 * another auth-state transition retriggers detection.
 */
@Composable
fun SessionExpiredModal(onSignInAgain: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_expired_title)) },
        text = { Text(stringResource(R.string.session_expired_body)) },
        confirmButton = {
            TextButton(onClick = onSignInAgain) {
                Text(stringResource(R.string.session_expired_action_sign_in))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.session_expired_action_dismiss))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun SessionExpiredModalPreview() {
    LapTheme {
        SessionExpiredModal(onSignInAgain = {}, onDismiss = {})
    }
}
