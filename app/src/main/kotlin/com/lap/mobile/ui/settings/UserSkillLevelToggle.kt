package com.lap.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lap.mobile.R
import com.lap.mobile.coaching.UserSkillLevel
import com.lap.mobile.ui.theme.LapTheme

private val GroupHorizontalPadding = 16.dp
private val GroupVerticalPadding = 12.dp
private val LabelColumnSpacing = 4.dp
private val OptionVerticalPadding = 8.dp
private val OptionLabelStartPadding = 8.dp

/**
 * Settings → User skill level radio group (T221). Stateless leaf
 * — caller observes [com.lap.mobile.coaching.UserSkillLevelPreference]
 * and passes the current selection plus the change handler.
 *
 * Three-way [RadioButton] group rather than a Switch because
 * skill level isn't binary — the user picks one of {BEGINNER,
 * INTERMEDIATE, EXPERT}. Material 3's [selectableGroup] +
 * `Role.RadioButton` semantics ensure TalkBack announces "1 of 3,
 * BEGINNER selected" / etc., matching standard Android radio
 * accessibility.
 *
 * Title + description follow the same row shape as T207 / T208 /
 * T218 toggles for visual consistency in the eventual Settings
 * host screen — only the control changes from Switch to a vertical
 * radio stack.
 *
 * Default is [UserSkillLevel.DEFAULT] (INTERMEDIATE) — see the
 * enum's KDoc for why intermediate is the safest fallback. The
 * Compose leaf doesn't enforce default; it just renders whatever
 * state the caller passes.
 *
 * Live device verification (the actual radio selection persists
 * across app restart, the eventual coaching prompt body changes
 * when the user changes skill level) is [PENDING: device-required]
 * — the @Preview fixtures lock the visual contract; the device
 * wiring lands when the Settings host screen ships.
 */
@Composable
fun UserSkillLevelToggle(
    selected: UserSkillLevel,
    onSelectedChange: (UserSkillLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GroupHorizontalPadding, vertical = GroupVerticalPadding)
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(LabelColumnSpacing),
    ) {
        Text(
            text = stringResource(R.string.user_skill_level_title),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.user_skill_level_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        UserSkillLevel.entries.forEach { level ->
            SkillLevelOption(
                level = level,
                selected = level == selected,
                onSelect = { onSelectedChange(level) },
            )
        }
    }
}

@Composable
private fun SkillLevelOption(level: UserSkillLevel, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .padding(vertical = OptionVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = stringResource(level.labelRes()),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = OptionLabelStartPadding),
        )
    }
}

private fun UserSkillLevel.labelRes(): Int = when (this) {
    UserSkillLevel.BEGINNER -> R.string.user_skill_level_beginner
    UserSkillLevel.INTERMEDIATE -> R.string.user_skill_level_intermediate
    UserSkillLevel.EXPERT -> R.string.user_skill_level_expert
}

@Preview(showBackground = true)
@Composable
private fun UserSkillLevelToggleBeginnerPreview() {
    LapTheme {
        UserSkillLevelToggle(selected = UserSkillLevel.BEGINNER, onSelectedChange = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun UserSkillLevelToggleIntermediatePreview() {
    LapTheme {
        UserSkillLevelToggle(selected = UserSkillLevel.INTERMEDIATE, onSelectedChange = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun UserSkillLevelToggleExpertPreview() {
    LapTheme {
        UserSkillLevelToggle(selected = UserSkillLevel.EXPERT, onSelectedChange = {})
    }
}
