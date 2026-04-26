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
 * Settings → Audio voice-announcement toggle (T207). Stateless leaf
 * — caller observes [com.lap.mobile.dashboard.VoiceAnnouncementPreference]
 * and passes the current `enabled` state plus the flip handler.
 *
 * Same row shape as T188 [BiometricLockToggle] — visual consistency
 * across Settings rows lets the eye scan toggles uniformly without
 * re-parsing each row's structure.
 *
 * The Settings host screen doesn't exist yet (no task has carved
 * one out), but this leaf is reusable wherever a one-line toggle
 * for voice announcements is wanted.
 *
 * Default-off semantics live in [com.lap.mobile.dashboard.VoiceAnnouncementPreference.DEFAULT_ENABLED]
 * — voice announcements interrupt music/podcasts/navigation, so
 * the toggle must be explicitly enabled by the user. The Compose
 * leaf doesn't enforce default — it just renders whatever state
 * the caller passes.
 *
 * Live device verification (the actual TextToSpeech engine speaks
 * the formatted lap announcement when a lap completes, the toggle
 * persists across app restart) is [PENDING: device-required] — the
 * @Preview fixtures lock the visual contract; the device wiring
 * lands when the Settings host screen and the LapAnnouncementSpeaker
 * Android impl land.
 */
@Composable
fun VoiceAnnouncementToggle(enabled: Boolean, onEnabledChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(LabelColumnSpacing)) {
            Text(
                text = stringResource(R.string.voice_announcement_toggle_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.voice_announcement_toggle_description),
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
private fun VoiceAnnouncementToggleOffPreview() {
    LapTheme {
        VoiceAnnouncementToggle(enabled = false, onEnabledChange = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VoiceAnnouncementToggleOnPreview() {
    LapTheme {
        VoiceAnnouncementToggle(enabled = true, onEnabledChange = {})
    }
}
