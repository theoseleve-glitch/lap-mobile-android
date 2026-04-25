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
import com.lap.mobile.dashboard.SpeedTraceProjector
import com.lap.mobile.dashboard.SpeedTraceSample
import com.lap.mobile.ui.theme.LapTheme

private val TraceWidth = 320.dp
private val TraceHeight = 120.dp
private val TraceCornerRadius = 12.dp
private val TraceStrokeWidth = 3.dp
private const val TRACE_BACKGROUND_ALPHA: Float = 0.05f
private const val TRACE_MIN_POINTS_TO_STROKE: Int = 2

/**
 * Speed-over-distance trace for the last lap (T196).
 *
 * Stateless leaf — caller passes the lap's [samples] directly
 * (typically from a future field on
 * [com.lap.mobile.dashboard.DashboardUiState], filled by T200+'s
 * data wiring). Projection is delegated to [SpeedTraceProjector] so
 * the JVM-tested kernel is the source of truth for the
 * (distance, speed) → canvas mapping; this Composable just strokes
 * a Path through the resolved coordinates.
 *
 * Visual: a 320×120dp rounded surface (12dp corners) with a faint
 * onSurface-tinted background (0.05 alpha — subtle so the trace
 * dominates), with the trace stroked in tertiary color (3dp stroke
 * width). The wider-than-tall aspect matches typical telemetry
 * software layouts where speed traces sit horizontally below or
 * beside the track map.
 *
 * Tertiary color (vs T195 TrackMap's primary, vs T193 SpeedGauge's
 * zone-dependent color) intentionally — three dashboard widgets
 * with three distinct theme accents lets the eye triangulate which
 * is which without reading labels.
 *
 * Edge cases:
 *   - 0 or 1 sample → blank rounded surface (clean cold-launch and
 *     pre-trace-built state).
 *   - 2+ samples → connected line segments.
 *
 * Performance: at typical 1200 samples/lap (2 minutes at 10Hz),
 * Path construction + stroke is O(N) and well under the 16ms/frame
 * budget. Same scratch-rebuild-each-composition strategy as T195's
 * TrackMap — no incremental Path mutation.
 *
 * @Preview fixtures cover empty (cold-launch) and a synthetic lap
 * with corner braking + straight acceleration shapes.
 *
 * Live device verification — actual rendering on real hardware
 * after a captured lap, no jank, trace shape is recognizable — is
 * [PENDING: device-required]. The previews lock the visual contract
 * and the JVM tests lock the projection math.
 */
@Composable
fun SpeedOverDistanceTrace(samples: List<SpeedTraceSample>, modifier: Modifier = Modifier) {
    val backgroundColor = MaterialTheme.colorScheme.onSurface.copy(alpha = TRACE_BACKGROUND_ALPHA)
    val strokeColor = MaterialTheme.colorScheme.tertiary
    Canvas(
        modifier = modifier
            .size(width = TraceWidth, height = TraceHeight)
            .clip(RoundedCornerShape(TraceCornerRadius))
            .background(backgroundColor),
    ) {
        if (samples.size < TRACE_MIN_POINTS_TO_STROKE) return@Canvas
        val projected = SpeedTraceProjector.project(samples, size.width, size.height)
        if (projected.size < TRACE_MIN_POINTS_TO_STROKE) return@Canvas
        val path = Path().apply {
            moveTo(projected[0].first, projected[0].second)
            for (i in 1 until projected.size) {
                lineTo(projected[i].first, projected[i].second)
            }
        }
        drawPath(
            path = path,
            color = strokeColor,
            style = Stroke(width = TraceStrokeWidth.toPx()),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SpeedOverDistanceTraceEmptyPreview() {
    LapTheme {
        SpeedOverDistanceTrace(samples = emptyList())
    }
}

@Preview(showBackground = true)
@Composable
private fun SpeedOverDistanceTraceSampleLapPreview() {
    LapTheme {
        SpeedOverDistanceTrace(samples = SAMPLE_LAP_TRACE)
    }
}

private val SAMPLE_LAP_TRACE: List<SpeedTraceSample> = listOf(
    // Synthetic last-lap shape: accel from start, brake into T1,
    // accel through T2, brake into T3, full straight to lap end.
    SpeedTraceSample(distanceMeters = 0.0, speedKmh = 60.0),
    SpeedTraceSample(distanceMeters = 100.0, speedKmh = 120.0),
    SpeedTraceSample(distanceMeters = 200.0, speedKmh = 180.0),
    SpeedTraceSample(distanceMeters = 280.0, speedKmh = 200.0),
    SpeedTraceSample(distanceMeters = 350.0, speedKmh = 80.0),
    SpeedTraceSample(distanceMeters = 450.0, speedKmh = 110.0),
    SpeedTraceSample(distanceMeters = 600.0, speedKmh = 160.0),
    SpeedTraceSample(distanceMeters = 700.0, speedKmh = 70.0),
    SpeedTraceSample(distanceMeters = 800.0, speedKmh = 130.0),
    SpeedTraceSample(distanceMeters = 1_000.0, speedKmh = 220.0),
    SpeedTraceSample(distanceMeters = 1_200.0, speedKmh = 240.0),
)
