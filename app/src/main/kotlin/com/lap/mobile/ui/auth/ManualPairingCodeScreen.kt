package com.lap.mobile.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lap.mobile.R
import com.lap.mobile.auth.ManualPairingCodeFormState
import com.lap.mobile.auth.ManualPairingCodeFormValidator
import com.lap.mobile.ui.theme.LapTheme

private val ScreenPadding = 24.dp
private val FieldSpacing = 12.dp

/**
 * Manual pairing code entry (T180) — stateless Compose screen that
 * fronts T179's [com.lap.mobile.auth.PairingRedeemUseCase] for users
 * whose camera path doesn't work.
 *
 * Shape mirrors [LoginScreen] (T172) intentionally — same OutlinedTextField
 * + primary Button + error slot — so a user who learned the login UX
 * yesterday doesn't have to re-learn this one. Only the labels and
 * the submit semantics differ.
 *
 * The submit handler receives the **trimmed** draft (manual entry is
 * paste-prone — leading/trailing whitespace from a copy-paste should
 * not break the redeem call). The state's stored draft keeps what
 * the user actually typed so they can debug a wrongly-pasted code by
 * looking at it.
 *
 * Keyboard config: `KeyboardCapitalization.None` because pairing
 * tokens are mixed-case ULIDs (`p_01JX9Z…`) — auto-capitalizing the
 * first character would break server-side equality checks. ImeAction
 * is `Done` (not `Send`) because some IMEs render `Send` as a paper
 * airplane and the visual cue is misleading for "verify this code".
 *
 * @Preview fixtures cover initial / ready-to-submit / submitting /
 * error so the Compose preview is the equivalent of T172's
 * 4-fixture coverage. Live device verification + actual redeem call
 * round-trip is [PENDING: device-required] until T179's HTTP impl
 * lands.
 */
@Composable
fun ManualPairingCodeScreen(state: ManualPairingCodeFormState, onCodeChange: (String) -> Unit, onSubmit: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(FieldSpacing),
        ) {
            Text(
                text = stringResource(R.string.manual_pairing_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.manual_pairing_help),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = state.draft,
                onValueChange = onCodeChange,
                label = { Text(stringResource(R.string.manual_pairing_code_label)) },
                singleLine = true,
                enabled = !state.isSubmitting,
                keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = onSubmit,
                enabled = ManualPairingCodeFormValidator.canSubmit(state),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text =
                    if (state.isSubmitting) {
                        stringResource(R.string.manual_pairing_progress_indicator)
                    } else {
                        stringResource(R.string.manual_pairing_submit)
                    },
                )
            }
        }
    }
}

// region Previews -------------------------------------------------------------

private const val PREVIEW_CODE: String = "p_01JX9Z8M4KTRP5D6Y3V2N7H8KQ"
private const val PREVIEW_ERROR: String = "That code isn't valid"

@Preview(showBackground = true)
@Composable
private fun ManualPairingCodeScreenInitialPreview() {
    LapTheme {
        ManualPairingCodeScreen(
            state = ManualPairingCodeFormState(),
            onCodeChange = {},
            onSubmit = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ManualPairingCodeScreenReadyToSubmitPreview() {
    LapTheme {
        ManualPairingCodeScreen(
            state = ManualPairingCodeFormState(draft = PREVIEW_CODE),
            onCodeChange = {},
            onSubmit = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ManualPairingCodeScreenSubmittingPreview() {
    LapTheme {
        ManualPairingCodeScreen(
            state = ManualPairingCodeFormState(draft = PREVIEW_CODE, isSubmitting = true),
            onCodeChange = {},
            onSubmit = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ManualPairingCodeScreenErrorPreview() {
    LapTheme {
        ManualPairingCodeScreen(
            state = ManualPairingCodeFormState(draft = PREVIEW_CODE, errorMessage = PREVIEW_ERROR),
            onCodeChange = {},
            onSubmit = {},
        )
    }
}

// endregion
