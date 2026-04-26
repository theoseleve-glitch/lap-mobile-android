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
 * Settings → Audio coaching-cue toggle (T218). Stateless leaf —
 * caller observes [com.lap.mobile.coaching.CoachingAudioCuePreference]
 * and passes the current `enabled` state plus the flip handler.
 *
 * Same row shape as T207
 * [VoiceAnnouncementToggle] and T188 [BiometricLockToggle] — visual
 * consistency across Settings rows lets the eye scan toggles
 * uniformly without re-parsing each row's structure.
 *
 * Default-off semantics live in
 * [com.lap.mobile.coaching.CoachingAudioCuePreference.DEFAULT_ENABLED]
 * — coaching audio cues interrupt music/podcasts/navigation, so
 * the toggle must be explicitly enabled by the user. The Compose
 * leaf doesn't enforce default — it just renders whatever state
 * the caller passes.
 *
 * `Modifier.semantics(mergeDescendants = true) {}` collapses the
 * row into one TalkBack focus target so screen-reader users hear
 * the toggle title + description as one announcement followed by
 * the Switch's checked state — matches T209 dashboard-row a11y
 * pattern.
 *
 * Live device verification (the actual TextToSpeech engine speaks
 * the formatted coaching cue when an insight fires, the toggle
 * persists across app restart) is [PENDING: device-required] —
 * the @Preview fixtures lock the visual contract; the device
 * wiring lands when the Settings host screen and the
 * CoachingAudioCueSpeaker Android impl land.
 */
@Composable
fun CoachingAudioCueToggle(enabled: Boolean, onEnabledChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
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
                text = stringResource(R.string.coaching_audio_cue_toggle_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.coaching_audio_cue_toggle_description),
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
private fun CoachingAudioCueToggleOffPreview() {
    LapTheme {
        CoachingAudioCueToggle(enabled = false, onEnabledChange = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun CoachingAudioCueToggleOnPreview() {
    LapTheme {
        CoachingAudioCueToggle(enabled = true, onEnabledChange = {})
    }
}
