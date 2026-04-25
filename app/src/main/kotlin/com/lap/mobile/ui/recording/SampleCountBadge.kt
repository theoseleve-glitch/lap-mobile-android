package com.lap.mobile.ui.recording

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lap.mobile.R
import com.lap.mobile.ui.theme.LapTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val BadgeHorizontalPadding = 12.dp
private val BadgeVerticalPadding = 4.dp
private val BadgeCornerRadius = 16.dp

private const val PREVIEW_ZERO_COUNT: Int = 0
private const val PREVIEW_MID_COUNT: Int = 1_234
private const val PREVIEW_LARGE_COUNT: Int = 27_650

/**
 * Live sample-count badge (T082). Stateless leaf — caller hoists the
 * count via `viewModel.sampleCount.collectAsState()` and passes the
 * `Int`. The reactive overload below wraps the same component for
 * direct StateFlow binding when the caller doesn't need to reshape
 * the value.
 *
 * Compose preview gates the layout at [assembleDebug] time; live
 * on-device rendering is `[PENDING: device-required]` for the
 * recording-screen instrumented test (T083).
 */
@Composable
fun SampleCountBadge(count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(BadgeCornerRadius),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = stringResource(R.string.recording_sample_count_badge, count),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(
                horizontal = BadgeHorizontalPadding,
                vertical = BadgeVerticalPadding,
            ),
        )
    }
}

/**
 * Reactive overload — collects [sampleCount] into Compose state and
 * forwards the latest value to the stateless [SampleCountBadge].
 * Defined as a separate function (not a default-arg overload) so the
 * stateless variant remains the previewable / instrumented-test
 * surface and the binding helper can evolve independently.
 */
@Composable
fun SampleCountBadge(sampleCount: StateFlow<Int>, modifier: Modifier = Modifier) {
    val count by sampleCount.collectAsState()
    SampleCountBadge(count = count, modifier = modifier)
}

@Preview(showBackground = true)
@Composable
private fun SampleCountBadgeZeroPreview() {
    LapTheme {
        SampleCountBadge(count = PREVIEW_ZERO_COUNT)
    }
}

@Preview(showBackground = true)
@Composable
private fun SampleCountBadgeMidPreview() {
    LapTheme {
        SampleCountBadge(count = PREVIEW_MID_COUNT)
    }
}

@Preview(showBackground = true)
@Composable
private fun SampleCountBadgeLargePreview() {
    LapTheme {
        SampleCountBadge(count = PREVIEW_LARGE_COUNT)
    }
}

@Preview(showBackground = true)
@Composable
private fun SampleCountBadgeReactivePreview() {
    LapTheme {
        SampleCountBadge(sampleCount = MutableStateFlow(PREVIEW_MID_COUNT))
    }
}
