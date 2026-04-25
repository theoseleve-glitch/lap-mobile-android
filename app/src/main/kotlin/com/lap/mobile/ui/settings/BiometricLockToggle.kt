package com.lap.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lap.mobile.R
import com.lap.mobile.ui.theme.LapTheme

private val RowHorizontalPadding = 16.dp
private val RowVerticalPadding = 12.dp
private val LabelColumnSpacing = 4.dp

/**
 * Settings → Security biometric lock toggle (T188). Stateless leaf —
 * caller observes [com.lap.mobile.auth.BiometricLockPreference] and
 * passes the current `enabled` state in plus the flip handler.
 *
 * The Settings host screen doesn't exist yet (no task has carved it
 * out — same situation as T181's PairedDeviceIndicator), but this
 * leaf is reusable wherever a one-line toggle for biometric lock is
 * wanted.
 *
 * The Switch's own `enabled` parameter (vs the toggle's `enabled`
 * which is its checked state) flexes via [interactable] so a Settings
 * host that's discovered the device has no biometric capability
 * (BiometricManager.canAuthenticate() returns
 * BIOMETRIC_ERROR_NO_HARDWARE etc.) can grey the row out without
 * removing it — leaving it visible communicates "this option exists
 * but isn't available on this device" rather than letting the user
 * wonder where it went.
 *
 * Live device verification (the actual BiometricPrompt appears,
 * fingerprint or face unlocks the gate, capability-detection greys
 * the toggle on a no-sensor emulator) is [PENDING: device-required] —
 * the @Preview fixtures lock the visual contract; the device wiring
 * lands when the Settings host screen does.
 */
@Composable
fun BiometricLockToggle(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    interactable: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(LabelColumnSpacing)) {
            Text(
                text = stringResource(R.string.biometric_lock_toggle_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text =
                if (interactable) {
                    stringResource(R.string.biometric_lock_toggle_description)
                } else {
                    stringResource(R.string.biometric_lock_toggle_unavailable)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            enabled = interactable,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BiometricLockToggleOffPreview() {
    LapTheme {
        BiometricLockToggle(enabled = false, onEnabledChange = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun BiometricLockToggleOnPreview() {
    LapTheme {
        BiometricLockToggle(enabled = true, onEnabledChange = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun BiometricLockToggleUnavailablePreview() {
    LapTheme {
        BiometricLockToggle(enabled = false, onEnabledChange = {}, interactable = false)
    }
}
