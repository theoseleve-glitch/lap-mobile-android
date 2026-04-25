package com.lap.mobile.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lap.mobile.dashboard.GgProjector
import com.lap.mobile.dashboard.GgSample
import com.lap.mobile.ui.theme.LapTheme
import kotlin.math.min

private val DiagramSide = 200.dp
private val DiagramCornerRadius = 12.dp
private val DotRadius = 2.dp
private val AxisStrokeWidth = 1.dp
private val FrictionCircleStrokeWidth = 1.dp
private const val DIAGRAM_BACKGROUND_ALPHA: Float = 0.05f
private const val AXIS_ALPHA: Float = 0.3f
private const val FRICTION_CIRCLE_ALPHA: Float = 0.2f
private const val DOT_ALPHA: Float = 0.6f

/**
 * G-G diagram (T197): scatter of (lateral g, longitudinal g)
 * samples inside a friction-circle reference, with crosshair axes.
 *
 * Stateless leaf — caller passes the [samples] list directly
 * (typically a snapshot from a future field on
 * [com.lap.mobile.dashboard.DashboardUiState], filled by T200+'s
 * IMU pipeline). Projection delegated to [GgProjector] so the
 * JVM-tested kernel is the source of truth for the (latG, lonG) →
 * canvas mapping; this Composable just draws dots + the reference
 * geometry.
 *
 * Visual:
 *   - 200×200dp rounded surface (12dp corners), faint background.
 *   - Crosshair axes through center (X = lateral, Y = longitudinal),
 *     30% alpha so they don't fight the dots for attention.
 *   - Friction-circle reference: a circle inscribed in the canvas
 *     at radius=halfCanvas, stroked at 20% alpha. The circle marks
 *     ±[GgProjector.DEFAULT_MAX_G] — dots inside the circle are
 *     within the chosen axis range, dots ON the circle indicate
 *     saturation.
 *   - Sample dots: small filled circles (2dp radius) in
 *     errorContainer color at 60% alpha. The semi-transparent fill
 *     means dot density at high-grip corners renders as visibly
 *     darker pools — a poor-man's heatmap without the Path-overlap
 *     cost.
 *
 * Color choice: errorContainer (red-tinted in light theme, deep red
 * in dark) keeps the diagram visually distinct from T193 SpeedGauge
 * (zone-coded), T195 TrackMap (primary), T196 SpeedOverDistanceTrace
 * (tertiary). Four widgets, four distinct accent identities.
 *
 * Square 1:1 aspect ratio matters for the friction-circle metaphor
 * — a non-square canvas would distort the circle into an ellipse
 * and lie about lat-vs-lon-g equivalence. Hardcoded 200dp×200dp
 * enforces this.
 *
 * @Preview fixtures cover empty (cold-launch) and a 12-point
 * synthetic envelope showing typical lap shape: braking dots at
 * bottom, accel dots at top, cornering dots at left/right.
 *
 * Live device verification — actual rendering on real hardware,
 * dot density visibly indicates grip-envelope edges, no jank with
 * 6000+ samples — is [PENDING: device-required]. Previews lock the
 * visual contract.
 */
@Composable
fun GgDiagram(samples: List<GgSample>, modifier: Modifier = Modifier) {
    val backgroundColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DIAGRAM_BACKGROUND_ALPHA)
    val axisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = AXIS_ALPHA)
    val frictionCircleColor = MaterialTheme.colorScheme.outline.copy(alpha = FRICTION_CIRCLE_ALPHA)
    val dotColor = MaterialTheme.colorScheme.error.copy(alpha = DOT_ALPHA)
    Canvas(
        modifier = modifier
            .size(DiagramSide)
            .clip(RoundedCornerShape(DiagramCornerRadius))
            .background(backgroundColor),
    ) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        // Friction-circle reference (inscribed circle).
        val radius = min(size.width, size.height) / 2f
        drawCircle(
            color = frictionCircleColor,
            radius = radius,
            center = Offset(centerX, centerY),
            style = Stroke(width = FrictionCircleStrokeWidth.toPx()),
        )
        // Crosshair axes.
        val axisPx = AxisStrokeWidth.toPx()
        drawLine(
            color = axisColor,
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = axisPx,
        )
        drawLine(
            color = axisColor,
            start = Offset(centerX, 0f),
            end = Offset(centerX, size.height),
            strokeWidth = axisPx,
        )
        // Sample dots.
        if (samples.isEmpty()) return@Canvas
        val projected = GgProjector.project(samples, size.width, size.height)
        val dotRadiusPx = DotRadius.toPx()
        projected.forEach { (x, y) ->
            drawCircle(
                color = dotColor,
                radius = dotRadiusPx,
                center = Offset(x, y),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GgDiagramEmptyPreview() {
    LapTheme {
        GgDiagram(samples = emptyList())
    }
}

@Preview(showBackground = true)
@Composable
private fun GgDiagramSampleEnvelopePreview() {
    LapTheme {
        GgDiagram(samples = SAMPLE_ENVELOPE)
    }
}

private val SAMPLE_ENVELOPE: List<GgSample> = listOf(
    // Synthetic envelope showing typical race-lap shape:
    // braking-into-corner pulls bottom-left/right, full accel
    // pushes top, cornering pulls sides.
    GgSample(latAccelG = 0.0, lonAccelG = -1.4),
    GgSample(latAccelG = -0.8, lonAccelG = -1.0),
    GgSample(latAccelG = -1.4, lonAccelG = -0.4),
    GgSample(latAccelG = -1.5, lonAccelG = 0.0),
    GgSample(latAccelG = -1.2, lonAccelG = 0.5),
    GgSample(latAccelG = -0.6, lonAccelG = 0.9),
    GgSample(latAccelG = 0.0, lonAccelG = 1.0),
    GgSample(latAccelG = 0.6, lonAccelG = 0.9),
    GgSample(latAccelG = 1.2, lonAccelG = 0.5),
    GgSample(latAccelG = 1.5, lonAccelG = 0.0),
    GgSample(latAccelG = 1.4, lonAccelG = -0.4),
    GgSample(latAccelG = 0.8, lonAccelG = -1.0),
)
