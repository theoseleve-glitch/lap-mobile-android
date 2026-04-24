package com.lap.mobile.ui.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import com.lap.mobile.data.SessionEntity
import com.lap.mobile.ui.theme.LapTheme
import com.lap.schemas.v1.SourceTag
import java.time.Instant

private val RowOuterPadding = 8.dp
private val RowInnerPadding = 12.dp
private val ActionSpacing = 8.dp

/**
 * Recovery modal shown at app launch when any startup reconciler
 * (T055 stale heartbeat, T057 stuck state) surfaces sessions that
 * died mid-capture. Each row offers Finalize (runs T059's Finalize-
 * from-journal path) or Discard (deletes buffer + DB row).
 *
 * The composable is stateless — the caller owns the list and the
 * callbacks — so previewing in Android Studio or instrumented tests
 * (T083+) just drives it with fixture lists.
 *
 * "⇒ Compose preview" acceptance: the three [Preview] fixtures below
 * cover single, multiple, and empty-but-visible states, and Gradle's
 * Compose compiler validates them at [assembleDebug] time. Live
 * visual check on-device is [PENDING: device-required].
 */
@Composable
fun RecoverySessionModal(
    sessions: List<SessionEntity>,
    onFinalize: (SessionEntity) -> Unit,
    onDiscard: (SessionEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recovery_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.recovery_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LazyColumn(modifier = Modifier.padding(top = RowOuterPadding)) {
                    items(sessions, key = { it.id }) { session ->
                        RecoveryRow(
                            session = session,
                            onFinalize = { onFinalize(session) },
                            onDiscard = { onDiscard(session) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.recovery_action_later))
            }
        },
    )
}

@Composable
private fun RecoveryRow(session: SessionEntity, onFinalize: () -> Unit, onDiscard: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = RowOuterPadding / 2),
    ) {
        Column(modifier = Modifier.padding(RowInnerPadding)) {
            Text(
                text = session.id,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = stringResource(
                    R.string.recovery_row_started_at,
                    session.startedAt?.toString() ?: "—",
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(
                    R.string.recovery_row_sample_count,
                    session.sampleCount,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = ActionSpacing),
                horizontalArrangement = Arrangement.spacedBy(
                    ActionSpacing,
                    Alignment.End,
                ),
            ) {
                TextButton(onClick = onDiscard) {
                    Text(stringResource(R.string.recovery_action_discard))
                }
                TextButton(onClick = onFinalize) {
                    Text(stringResource(R.string.recovery_action_finalize))
                }
            }
        }
    }
}

// region Previews -------------------------------------------------------------

private val PreviewSessionA: SessionEntity = SessionEntity(
    id = "0194aaaa-aaaa-7aaa-aaaa-aaaaaaaaaaaa",
    state = "recording",
    source = SourceTag.REAL,
    startedAt = Instant.parse("2026-04-24T09:00:00Z"),
    trackId = "0194cccc-cccc-7ccc-cccc-cccccccccccc",
    carId = "0194dddd-dddd-7ddd-dddd-dddddddddddd",
    sampleCount = 12_340,
)

private val PreviewSessionB: SessionEntity = SessionEntity(
    id = "0194bbbb-bbbb-7bbb-bbbb-bbbbbbbbbbbb",
    state = "finalizing",
    source = SourceTag.IRACING,
    startedAt = Instant.parse("2026-04-23T15:22:00Z"),
    sampleCount = 27_000,
)

@Preview(showBackground = true)
@Composable
private fun RecoverySessionModalSinglePreview() {
    LapTheme {
        Surface {
            RecoverySessionModal(
                sessions = listOf(PreviewSessionA),
                onFinalize = {},
                onDiscard = {},
                onDismiss = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecoverySessionModalMultiplePreview() {
    LapTheme {
        Surface {
            RecoverySessionModal(
                sessions = listOf(PreviewSessionA, PreviewSessionB),
                onFinalize = {},
                onDiscard = {},
                onDismiss = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecoverySessionModalEmptyPreview() {
    LapTheme {
        Surface {
            RecoverySessionModal(
                sessions = emptyList(),
                onFinalize = {},
                onDiscard = {},
                onDismiss = {},
            )
        }
    }
}

// endregion
