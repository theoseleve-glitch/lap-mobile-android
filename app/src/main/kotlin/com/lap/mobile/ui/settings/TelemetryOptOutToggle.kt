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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lap.mobile.R
import com.lap.mobile.ui.theme.LapTheme

private val RowHorizontalPadding = 16.dp
private val RowVerticalPadding = 12.dp
private val LabelColumnSpacing = 4.dp

/**
 * Settings → Telemetry opt-out toggle (T224). Stateless leaf —
 * caller observes [com.lap.mobile.core.TelemetryOptOutPreference]
 * and passes the current `enabled` state (= NOT opted-out) plus
 * the flip handler.
 *
 * UX inversion vs the pref: the Switch reads as "share crash/
 * usage reports" (checked = sharing-enabled) which matches user
 * intuition ("toggle is ON → I'm sharing data"). The pref
 * encodes the opt-out side (`isOptedOut() == true → don't
 * share`), so the call site bridges:
 *   - `enabled = !pref.isOptedOut()`
 *   - `onEnabledChange(new) → pref.setOptedOut(!new)`
 *
 * Same row shape as T207 [VoiceAnnouncementToggle], T208
 * [HighContrastThemeToggle], T218 [CoachingAudioCueToggle] —
 * visual consistency across Settings rows lets the eye scan
 * toggles uniformly. `Modifier.semantics(mergeDescendants = true)`
 * collapses the row into one TalkBack focus target so screen-
 * reader users hear title + description as one announcement
 * followed by the Switch's checked state, matching T209
 * dashboard-row a11y pattern.
 *
 * Live device verification (toggle persists across app restart,
 * Sentry + PostHog actually stop transmitting after a flip) is
 * [PENDING: device-required] — the @Preview fixtures lock the
 * visual contract; the suppression invariant is JVM-locked by
 * [com.lap.mobile.core.OptOutAwareSentryReporterTest] +
 * [com.lap.mobile.core.OptOutAwareAnalyticsReporterTest].
 */
@Composable
fun TelemetryOptOutToggle(enabled: Boolean, onEnabledChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(LabelColumnSpacing)) {
            Text(
                text = stringResource(R.string.telemetry_opt_out_toggle_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.telemetry_opt_out_toggle_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TelemetryOptOutToggleOnPreview() {
    LapTheme {
        TelemetryOptOutToggle(enabled = true, onEnabledChange = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun TelemetryOptOutToggleOffPreview() {
    LapTheme {
        TelemetryOptOutToggle(enabled = false, onEnabledChange = {})
    }
}
