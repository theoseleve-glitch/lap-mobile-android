package com.lap.mobile.ui.uploads

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lap.mobile.R
import com.lap.mobile.ui.theme.LapTheme
import com.lap.mobile.upload.UploadBadgeState

private val BadgeHorizontalPadding = 8.dp
private val BadgeVerticalPadding = 4.dp
private const val MIDDLE_PROGRESS_PERCENT_FOR_PREVIEW: Int = 42

/**
 * Tiny one-line pill rendering one of the four [UploadBadgeState]
 * variants (T164). Stateless: the caller passes a resolved state from
 * [com.lap.mobile.upload.UploadBadgeStateResolver] so this composable
 * never has to touch Room or the [com.lap.mobile.upload.UploadProgressBus].
 *
 * Color tagging via [MaterialTheme.colorScheme] semantic roles instead
 * of raw hex: `tertiaryContainer` for in-flight (neutral attention),
 * `primaryContainer` for cloud-confirmed (positive), `errorContainer`
 * for failed (negative), `surfaceVariant` for local (calm "nothing yet"
 * tone). When the user later swaps to a dark theme or a colorblind
 * palette, badges adapt automatically.
 *
 * "⇒ badges" acceptance: each variant gets a [Preview] fixture below
 * (4 total — Local / Uploading / CloudConfirmed / Failed), and the
 * Compose compiler validates them at [assembleDebug] time. Live visual
 * check on-device is [PENDING: device-required].
 */
@Composable
fun UploadBadge(state: UploadBadgeState, modifier: Modifier = Modifier) {
    val (label, container, content) = when (state) {
        UploadBadgeState.Local -> Triple(
            stringResource(R.string.upload_badge_local),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is UploadBadgeState.Uploading -> Triple(
            stringResource(R.string.upload_badge_uploading, state.progressPercent),
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        UploadBadgeState.CloudConfirmed -> Triple(
            stringResource(R.string.upload_badge_cloud_confirmed),
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        UploadBadgeState.Failed -> Triple(
            stringResource(R.string.upload_badge_failed),
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
    }
    BadgePill(label = label, containerColor = container, contentColor = content, modifier = modifier)
}

@Composable
private fun BadgePill(label: String, containerColor: Color, contentColor: Color, modifier: Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(
                horizontal = BadgeHorizontalPadding,
                vertical = BadgeVerticalPadding,
            ),
        )
    }
}

// region Previews -------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun UploadBadgeLocalPreview() {
    LapTheme { Surface { UploadBadge(state = UploadBadgeState.Local) } }
}

@Preview(showBackground = true)
@Composable
private fun UploadBadgeUploadingPreview() {
    LapTheme {
        Surface { UploadBadge(state = UploadBadgeState.Uploading(MIDDLE_PROGRESS_PERCENT_FOR_PREVIEW)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun UploadBadgeCloudConfirmedPreview() {
    LapTheme { Surface { UploadBadge(state = UploadBadgeState.CloudConfirmed) } }
}

@Preview(showBackground = true)
@Composable
private fun UploadBadgeFailedPreview() {
    LapTheme { Surface { UploadBadge(state = UploadBadgeState.Failed) } }
}

// endregion
