package com.lap.mobile.ui.uploads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lap.mobile.R
import com.lap.mobile.data.UploadQueueEntity
import com.lap.mobile.ui.theme.LapTheme
import com.lap.mobile.upload.UploadsScreenState
import com.lap.schemas.v1.UUID7

private val ScreenPadding = 16.dp
private val SectionSpacing = 12.dp
private val RowOuterPadding = 8.dp
private val RowInnerPadding = 12.dp
private val ActionSpacing = 8.dp

/**
 * Uploads dashboard (T163). Two stacked sections — "Uploading" (active
 * queued rows) and "Failed" (failed_permanent rows) — driven by a
 * [UploadsScreenState] computed by [com.lap.mobile.upload.UploadsScreenPartition].
 *
 * The composable is stateless: the caller (a future ViewModel) owns the
 * state Flow + the retry/abandon callbacks. That keeps the screen
 * trivial to preview here and to host inside a NavHost destination.
 *
 * Action callbacks fire [com.lap.mobile.upload.ManualRetryUseCase] (T161)
 * and [com.lap.mobile.upload.AbandonUploadUseCase] (T162) at the
 * application layer — kept out of the composable so the UI doesn't
 * depend on Hilt or coroutine scopes.
 *
 * "⇒ Compose preview" acceptance: the four [Preview] fixtures below
 * cover empty / active-only / failed-only / mixed, and the Compose
 * compiler validates them at [assembleDebug] time. Live visual check
 * on-device is [PENDING: device-required].
 */
@Composable
fun UploadsScreen(state: UploadsScreenState, onRetry: (UUID7) -> Unit, onAbandon: (UUID7) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
    ) {
        Text(
            text = stringResource(R.string.uploads_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        UploadsSection(
            title = stringResource(R.string.uploads_section_active),
            emptyMessage = stringResource(R.string.uploads_empty_active),
            rows = state.active,
            showRetry = false,
            onRetry = onRetry,
            onAbandon = onAbandon,
        )
        UploadsSection(
            title = stringResource(R.string.uploads_section_failed),
            emptyMessage = stringResource(R.string.uploads_empty_failed),
            rows = state.failed,
            showRetry = true,
            onRetry = onRetry,
            onAbandon = onAbandon,
        )
    }
}

@Composable
private fun UploadsSection(
    title: String,
    emptyMessage: String,
    rows: List<UploadQueueEntity>,
    showRetry: Boolean,
    onRetry: (UUID7) -> Unit,
    onAbandon: (UUID7) -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        if (rows.isEmpty()) {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = RowOuterPadding),
            )
        } else {
            LazyColumn(modifier = Modifier.padding(top = RowOuterPadding)) {
                items(rows, key = { it.sessionId }) { row ->
                    UploadsRow(
                        row = row,
                        showRetry = showRetry,
                        onRetry = { onRetry(row.sessionId) },
                        onAbandon = { onAbandon(row.sessionId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UploadsRow(row: UploadQueueEntity, showRetry: Boolean, onRetry: () -> Unit, onAbandon: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = RowOuterPadding / 2),
    ) {
        Column(modifier = Modifier.padding(RowInnerPadding)) {
            Text(
                text = row.sessionId,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = stringResource(
                    R.string.uploads_row_chunks_progress,
                    row.chunksSent,
                    row.chunksTotal,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.uploads_row_attempt_count, row.attemptCount),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = ActionSpacing),
                horizontalArrangement = Arrangement.spacedBy(ActionSpacing, Alignment.End),
            ) {
                TextButton(onClick = onAbandon) {
                    Text(stringResource(R.string.uploads_action_abandon))
                }
                if (showRetry) {
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.uploads_action_retry))
                    }
                }
            }
        }
    }
}

// region Previews -------------------------------------------------------------

private const val PREVIEW_SESSION_ACTIVE_OLDEST: UUID7 = "0194aaaa-aaaa-7aaa-aaaa-aaaaaaaaaaaa"
private const val PREVIEW_SESSION_ACTIVE_NEWER: UUID7 = "0194bbbb-bbbb-7bbb-bbbb-bbbbbbbbbbbb"
private const val PREVIEW_SESSION_FAILED: UUID7 = "0194cccc-cccc-7ccc-cccc-cccccccccccc"
private const val PREVIEW_IDEMPOTENCY_KEY_ACTIVE_OLDEST: String = "key-active-oldest"
private const val PREVIEW_IDEMPOTENCY_KEY_ACTIVE_NEWER: String = "key-active-newer"
private const val PREVIEW_IDEMPOTENCY_KEY_FAILED: String = "key-failed"
private const val PREVIEW_CHUNKS_TOTAL: Int = 8
private const val PREVIEW_CHUNKS_SENT_PARTIAL: Int = 3
private const val PREVIEW_FAILED_ATTEMPT_COUNT: Int = 10
private const val PREVIEW_FAILED_HTTP_CODE: Int = 502

private val PreviewActiveOldest: UploadQueueEntity = UploadQueueEntity(
    sessionId = PREVIEW_SESSION_ACTIVE_OLDEST,
    state = "queued",
    chunksTotal = PREVIEW_CHUNKS_TOTAL,
    idempotencyKey = PREVIEW_IDEMPOTENCY_KEY_ACTIVE_OLDEST,
    chunksSent = PREVIEW_CHUNKS_SENT_PARTIAL,
)

private val PreviewActiveNewer: UploadQueueEntity = UploadQueueEntity(
    sessionId = PREVIEW_SESSION_ACTIVE_NEWER,
    state = "queued",
    chunksTotal = PREVIEW_CHUNKS_TOTAL,
    idempotencyKey = PREVIEW_IDEMPOTENCY_KEY_ACTIVE_NEWER,
)

private val PreviewFailed: UploadQueueEntity = UploadQueueEntity(
    sessionId = PREVIEW_SESSION_FAILED,
    state = "failed_permanent",
    chunksTotal = PREVIEW_CHUNKS_TOTAL,
    idempotencyKey = PREVIEW_IDEMPOTENCY_KEY_FAILED,
    attemptCount = PREVIEW_FAILED_ATTEMPT_COUNT,
    lastHttpCode = PREVIEW_FAILED_HTTP_CODE,
    lastError = "Bad Gateway",
)

@Preview(showBackground = true)
@Composable
private fun UploadsScreenEmptyPreview() {
    LapTheme {
        Surface {
            UploadsScreen(state = UploadsScreenState.EMPTY, onRetry = {}, onAbandon = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UploadsScreenActiveOnlyPreview() {
    LapTheme {
        Surface {
            UploadsScreen(
                state = UploadsScreenState(
                    active = listOf(PreviewActiveOldest, PreviewActiveNewer),
                    failed = emptyList(),
                ),
                onRetry = {},
                onAbandon = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UploadsScreenFailedOnlyPreview() {
    LapTheme {
        Surface {
            UploadsScreen(
                state = UploadsScreenState(active = emptyList(), failed = listOf(PreviewFailed)),
                onRetry = {},
                onAbandon = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UploadsScreenMixedPreview() {
    LapTheme {
        Surface {
            UploadsScreen(
                state = UploadsScreenState(
                    active = listOf(PreviewActiveOldest, PreviewActiveNewer),
                    failed = listOf(PreviewFailed),
                ),
                onRetry = {},
                onAbandon = {},
            )
        }
    }
}

// endregion
