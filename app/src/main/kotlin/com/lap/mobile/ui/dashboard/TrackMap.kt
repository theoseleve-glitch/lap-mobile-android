package com.lap.mobile.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lap.mobile.dashboard.GpsPoint
import com.lap.mobile.dashboard.TrackProjector
import com.lap.mobile.ui.theme.LapTheme

private val MapWidth = 280.dp
private val MapHeight = 200.dp
private val MapCornerRadius = 12.dp
private val TrailStrokeWidth = 4.dp
private const val MAP_BACKGROUND_ALPHA: Float = 0.05f
private const val TRAIL_MIN_POINTS_TO_STROKE: Int = 2

/**
 * GPS-trail breadcrumb (T195).
 *
 * Stateless leaf — caller passes the [trail] directly (typically a
 * snapshot from a future field on
 * [com.lap.mobile.dashboard.DashboardUiState], filled by T196+'s
 * data wiring). Projection is delegated to [TrackProjector] so the
 * JVM-tested kernel is the source of truth for the lat/lon → canvas
 * mapping; this Composable just strokes a Path through the resolved
 * coordinates.
 *
 * Visual: a 280×200dp rounded surface with a faint
 * onSurface-tinted background (0.05 alpha — subtle so the trail
 * dominates), with the breadcrumb stroked in primary color (4dp
 * stroke width).
 *
 * Performance: at 10Hz updates and ~6000 points/lap, the per-frame
 * cost is dominated by Path construction (O(N)) and Canvas stroke
 * (also O(N)). Both run well under the 16ms/frame budget. The
 * Path is rebuilt from scratch each composition rather than
 * mutated incrementally — Compose snapshots the trail List anyway,
 * and incremental Path mutation is more bug-surface than perf win
 * for sub-10000-point trails.
 *
 * Edge cases:
 *   - 0 or 1 point → blank rounded surface (clean cold-launch and
 *     pre-trail-built state). A single-point dot overlay can land
 *     as a separate "current position" concern in T196+.
 *   - 2+ points → connected line segments.
 *
 * @Preview fixtures cover empty (cold-launch) and a simulated
 * rectangular lap.
 *
 * Live device verification — actual 10Hz append-and-redraw on real
 * hardware, no jank — is [PENDING: device-required]. The previews
 * lock the visual contract at assembleDebug.
 */
@Composable
fun TrackMap(trail: List<GpsPoint>, modifier: Modifier = Modifier) {
    val backgroundColor = MaterialTheme.colorScheme.onSurface.copy(alpha = MAP_BACKGROUND_ALPHA)
    val strokeColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = modifier
            .size(width = MapWidth, height = MapHeight)
            .clip(RoundedCornerShape(MapCornerRadius))
            .background(backgroundColor),
    ) {
        if (trail.size < TRAIL_MIN_POINTS_TO_STROKE) return@Canvas
        val projected = TrackProjector.project(trail, size.width, size.height)
        if (projected.size < TRAIL_MIN_POINTS_TO_STROKE) return@Canvas
        val path = Path().apply {
            moveTo(projected[0].first, projected[0].second)
            for (i in 1 until projected.size) {
                lineTo(projected[i].first, projected[i].second)
            }
        }
        drawPath(
            path = path,
            color = strokeColor,
            style = Stroke(width = TrailStrokeWidth.toPx()),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TrackMapEmptyPreview() {
    LapTheme {
        TrackMap(trail = emptyList())
    }
}

@Preview(showBackground = true)
@Composable
private fun TrackMapSampleLapPreview() {
    LapTheme {
        TrackMap(trail = SAMPLE_RECT_LAP)
    }
}

private val SAMPLE_RECT_LAP: List<GpsPoint> = listOf(
    GpsPoint(latitudeDeg = 45.0000, longitudeDeg = 5.0000),
    GpsPoint(latitudeDeg = 45.0000, longitudeDeg = 5.0050),
    GpsPoint(latitudeDeg = 45.0010, longitudeDeg = 5.0100),
    GpsPoint(latitudeDeg = 45.0030, longitudeDeg = 5.0110),
    GpsPoint(latitudeDeg = 45.0050, longitudeDeg = 5.0100),
    GpsPoint(latitudeDeg = 45.0050, longitudeDeg = 5.0050),
    GpsPoint(latitudeDeg = 45.0050, longitudeDeg = 5.0000),
    GpsPoint(latitudeDeg = 45.0040, longitudeDeg = 4.9990),
    GpsPoint(latitudeDeg = 45.0030, longitudeDeg = 4.9980),
    GpsPoint(latitudeDeg = 45.0020, longitudeDeg = 4.9990),
    GpsPoint(latitudeDeg = 45.0010, longitudeDeg = 5.0000),
    GpsPoint(latitudeDeg = 45.0000, longitudeDeg = 5.0000),
)
