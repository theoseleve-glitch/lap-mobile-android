package com.lap.mobile.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lap.mobile.R
import com.lap.mobile.auth.LoginFormState
import com.lap.mobile.auth.LoginFormValidator
import com.lap.mobile.ui.theme.LapTheme

private val ScreenPadding = 24.dp
private val FieldSpacing = 12.dp

/**
 * Compose login screen (T172). Stateless: renders [LoginFormState] and
 * fires callbacks for each user action. The future ViewModel (post
 * Block-I toolchain bump — see project_clerk_toolchain_gap memory)
 * owns the state Flow and dispatches Clerk sign-in calls; this
 * screen has no Clerk import on the classpath, which is what lets it
 * compile + preview today while T171 stays blocked.
 *
 * Submit-button enablement is decided by [LoginFormValidator] — both
 * fields non-blank AND no submit currently in flight. The Composable
 * intentionally doesn't re-derive that locally; locking it in one
 * place means the JVM-tested kernel and the rendered button agree.
 *
 * Email-format / password-strength validation is deliberately NOT
 * here; the canonical check is server-side (Clerk), and surfacing its
 * response via [LoginFormState.errorMessage] is more honest than
 * client-side guesses.
 *
 * "⇒ Compose preview" acceptance: 4 [Preview] fixtures (initial /
 * filled-ready / submitting / error) compile-checked at
 * `assembleDebug`. Live device check + Clerk wire-up remain
 * [PENDING: device-required] for T171 toolchain bump.
 */
@Composable
fun LoginScreen(
    state: LoginFormState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onGoogleSignIn: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(FieldSpacing),
        ) {
            Text(
                text = stringResource(R.string.login_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            OutlinedTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = { Text(stringResource(R.string.login_email_label)) },
                singleLine = true,
                enabled = !state.isSubmitting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.login_password_label)) },
                singleLine = true,
                enabled = !state.isSubmitting,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
                enabled = LoginFormValidator.canSubmit(state),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (state.isSubmitting) {
                        stringResource(R.string.login_progress_indicator)
                    } else {
                        stringResource(R.string.login_submit)
                    },
                )
            }
            OutlinedButton(
                onClick = onGoogleSignIn,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.login_google))
            }
        }
    }
}

// region Previews -------------------------------------------------------------

private const val PREVIEW_EMAIL: String = "driver@example.com"
private const val PREVIEW_PASSWORD: String = "correct-horse-battery-staple"
private const val PREVIEW_ERROR: String = "Invalid email or password"

@Preview(showBackground = true)
@Composable
private fun LoginScreenInitialPreview() {
    LapTheme {
        LoginScreen(
            state = LoginFormState(),
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onGoogleSignIn = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenReadyToSubmitPreview() {
    LapTheme {
        LoginScreen(
            state = LoginFormState(email = PREVIEW_EMAIL, password = PREVIEW_PASSWORD),
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onGoogleSignIn = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenSubmittingPreview() {
    LapTheme {
        LoginScreen(
            state = LoginFormState(
                email = PREVIEW_EMAIL,
                password = PREVIEW_PASSWORD,
                isSubmitting = true,
            ),
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onGoogleSignIn = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenErrorPreview() {
    LapTheme {
        LoginScreen(
            state = LoginFormState(
                email = PREVIEW_EMAIL,
                password = PREVIEW_PASSWORD,
                errorMessage = PREVIEW_ERROR,
            ),
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onGoogleSignIn = {},
        )
    }
}

// endregion
