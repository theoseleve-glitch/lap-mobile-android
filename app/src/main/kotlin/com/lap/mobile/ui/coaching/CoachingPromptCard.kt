package com.lap.mobile.ui.coaching

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lap.mobile.R
import com.lap.mobile.coaching.InsightPromptPayload
import com.lap.mobile.coaching.InsightSeverity
import com.lap.mobile.ui.theme.LapTheme

private val CardOuterPadding = 16.dp
private val CardInnerPadding = 16.dp
private val TitleBodySpacing = 8.dp

/**
 * Bottom-sheet-style coaching prompt card (T217) — stateless leaf
 * that renders a single [InsightPromptPayload] above a Dismiss
 * button. Caller positions it (typically aligned to bottom of the
 * dashboard during recording).
 *
 * Why a stateless Card not a [androidx.compose.material3.ModalBottomSheet]:
 *   - The card needs to coexist with the recording dashboard,
 *     not modally cover it (driver still needs sample-rate / GPS /
 *     battery indicators visible while reading the tip).
 *   - Bottom-sheet behavior (drag-to-dismiss, snap points,
 *     scrim) is a separate UX decision T219+ may layer on. T217
 *     locks the visual content; the integrating screen owns
 *     positioning + sheet behavior.
 *
 * Theme:
 *   - [InsightSeverity.LOW]   → surfaceVariant container, neutral
 *     hint that doesn't demand attention.
 *   - [InsightSeverity.MEDIUM] → tertiaryContainer container, the
 *     standard coaching tip — accented but not alarming. Default
 *     for missing/unknown severities (per [InsightPromptPayload]
 *     forward-compat policy).
 *   - [InsightSeverity.HIGH]  → errorContainer container, signals
 *     "fix-this" urgency — same color slot the dashboard reserves
 *     for warning pills (T198 NoFix, T200 Disconnected, T201
 *     LowWarning) so the visual language is consistent.
 *
 * Color-blind safety: the severity badge text ("LOW" / "MEDIUM" /
 * "HIGH") is shown in the title row alongside the color. Same
 * color-plus-text reinforcement as T202's recording strip.
 *
 * TalkBack: the entire card carries a single composite
 * `contentDescription` that linearises severity + title + body
 * so screen-reader users hear "[severity] coaching insight: title.
 * body." rather than three disconnected cells. Same
 * `semantics(mergeDescendants = true)` pattern as T209's accessible
 * dashboard rows.
 *
 * @Preview fixtures cover all three severity tiers + a long-body
 * case so the card's wrap behavior is visually verified at design
 * time. Live on-device verification — actual sheet behavior, IME
 * insets, integration with the dashboard scaffold — is [PENDING:
 * device-required].
 */
@Composable
fun CoachingPromptCard(payload: InsightPromptPayload, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val containerColor = payload.severity.containerColor()
    val contentColor = payload.severity.contentColor()
    val severityLabel = payload.severity.label()
    val a11yDescription = stringResource(
        R.string.coaching_prompt_a11y_description_format,
        severityLabel,
        payload.title,
        payload.body,
    )
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(CardOuterPadding)
            .semantics(mergeDescendants = true) {
                contentDescription = a11yDescription
            },
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Column(modifier = Modifier.padding(CardInnerPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = severityLabel,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(TitleBodySpacing))
            Text(
                text = payload.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(TitleBodySpacing))
            Text(
                text = payload.body,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.coaching_prompt_dismiss_action))
                }
            }
        }
    }
}

@Composable
private fun InsightSeverity.containerColor(): Color = when (this) {
    InsightSeverity.LOW -> MaterialTheme.colorScheme.surfaceVariant
    InsightSeverity.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer
    InsightSeverity.HIGH -> MaterialTheme.colorScheme.errorContainer
}

@Composable
private fun InsightSeverity.contentColor(): Color = when (this) {
    InsightSeverity.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
    InsightSeverity.MEDIUM -> MaterialTheme.colorScheme.onTertiaryContainer
    InsightSeverity.HIGH -> MaterialTheme.colorScheme.onErrorContainer
}

@Composable
private fun InsightSeverity.label(): String = stringResource(
    when (this) {
        InsightSeverity.LOW -> R.string.coaching_prompt_severity_low
        InsightSeverity.MEDIUM -> R.string.coaching_prompt_severity_medium
        InsightSeverity.HIGH -> R.string.coaching_prompt_severity_high
    },
)

// region Previews -------------------------------------------------------------

private val PreviewLowPayload = InsightPromptPayload(
    ruleId = "rule-soft-tip",
    title = "Throttle modulation at Turn 5",
    body = "You can be slightly more progressive on throttle exit — try a smoother application.",
    severity = InsightSeverity.LOW,
)

private val PreviewMediumPayload = InsightPromptPayload(
    ruleId = "rule-late-braking",
    title = "Late braking at Turn 3",
    body = "Try braking 30m earlier to carry more apex speed.",
    severity = InsightSeverity.MEDIUM,
)

private val PreviewHighPayload = InsightPromptPayload(
    ruleId = "rule-trail-brake-overshoot",
    title = "Trail brake overshoot",
    body = "You're carrying brake well past the apex — release earlier or you'll oversteer on next lap.",
    severity = InsightSeverity.HIGH,
)

private val PreviewLongBodyPayload = InsightPromptPayload(
    ruleId = "rule-detailed-tip",
    title = "Sector 2 line consistency",
    body = "Across the last three laps your apex point at Turn 7 has drifted 1.5m wider each time. " +
        "This usually means brake release timing is shifting slightly later — focus on releasing the " +
        "brake at the same reference point so the car settles into the same line.",
    severity = InsightSeverity.MEDIUM,
)

@Preview(showBackground = true)
@Composable
private fun CoachingPromptCardLowPreview() {
    LapTheme { CoachingPromptCard(payload = PreviewLowPayload, onDismiss = {}) }
}

@Preview(showBackground = true)
@Composable
private fun CoachingPromptCardMediumPreview() {
    LapTheme { CoachingPromptCard(payload = PreviewMediumPayload, onDismiss = {}) }
}

@Preview(showBackground = true)
@Composable
private fun CoachingPromptCardHighPreview() {
    LapTheme { CoachingPromptCard(payload = PreviewHighPayload, onDismiss = {}) }
}

@Preview(showBackground = true)
@Composable
private fun CoachingPromptCardLongBodyPreview() {
    LapTheme { CoachingPromptCard(payload = PreviewLongBodyPayload, onDismiss = {}) }
}

// endregion
