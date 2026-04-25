package com.lap.mobile.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CANVAS_WIDTH: Float = 200f
private const val CANVAS_HEIGHT: Float = 100f
private const val PROJECT_TOLERANCE: Float = 0.01f

/**
 * Locks the contract for [SpeedTraceProjector] (T196) — the pure
 * function that turns a last-lap speed-over-distance series into
 * canvas coordinates the
 * [com.lap.mobile.ui.dashboard.SpeedOverDistanceTrace] Compose
 * Canvas can stroke as a 2D chart.
 *
 * Why a kernel split (projector separate from the Compose chart):
 * 2D-chart projection is the part that determines whether the trace
 * looks right. Bugs here render as visually-broken-traces; JVM-
 * testing the math means a Composable refactor can't drift the
 * result.
 *
 * Projection model: linear bounds-fit, same shape as T195's
 * [TrackProjector] but operating on (distance, speed) instead of
 * (lon, lat). Distance grows along X (left-to-right). Y is inverted
 * because canvas Y grows down but speed grows up: high speed lands
 * near the top, low speed near the bottom — matches every chart-
 * convention drivers see.
 *
 * Bounds are auto-fit to the trace's own range — last-lap-only
 * scope means we don't need a fixed Y axis (e.g., "always 0–250
 * km/h"); a future multi-lap overlay can switch to a fixed axis
 * without changing the call site.
 *
 * Defensive cases (locked here):
 *   - Empty input → empty output (chart draws nothing).
 *   - Single sample → centered (no division by zero).
 *   - Constant-distance samples (driver standing still emitting
 *     samples) → x collapses to canvas center; y spreads normally.
 *   - Constant-speed samples (cruise control hypothetical) → y
 *     collapses to canvas center; x spreads normally.
 *   - Order preserved (chart is drawn in capture sequence).
 */
class SpeedTraceProjectorTest {
    @Test
    fun empty_trace_projects_to_empty_so_pre_lap_canvas_draws_nothing_not_a_phantom_dot() {
        val result = SpeedTraceProjector.project(
            samples = emptyList(),
            canvasWidthPx = CANVAS_WIDTH,
            canvasHeightPx = CANVAS_HEIGHT,
        )

        assertTrue("expected empty projection, got: $result", result.isEmpty())
    }

    @Test
    fun single_sample_projects_to_canvas_center_so_first_sample_pre_trace_draws_a_centered_dot() {
        val result = SpeedTraceProjector.project(
            samples = listOf(SpeedTraceSample(distanceMeters = 100.0, speedKmh = 80.0)),
            canvasWidthPx = CANVAS_WIDTH,
            canvasHeightPx = CANVAS_HEIGHT,
        )

        assertEquals(1, result.size)
        assertEquals(CANVAS_WIDTH / 2f, result[0].first, PROJECT_TOLERANCE)
        assertEquals(CANVAS_HEIGHT / 2f, result[0].second, PROJECT_TOLERANCE)
    }

    @Test
    fun two_samples_at_diagonal_extremes_project_to_canvas_corners_so_bounds_fit_axis_stretches_to_canvas_extents() {
        // Headline T196 invariant: trace extremes map to canvas
        // extremes. Lap-start (distance=0, slow) → bottom-left;
        // lap-end (max distance, fast) → top-right (Y inverted
        // because canvas Y grows down).
        val start = SpeedTraceSample(distanceMeters = 0.0, speedKmh = 50.0)
        val end = SpeedTraceSample(distanceMeters = 1_000.0, speedKmh = 200.0)

        val result = SpeedTraceProjector.project(
            samples = listOf(start, end),
            canvasWidthPx = CANVAS_WIDTH,
            canvasHeightPx = CANVAS_HEIGHT,
        )

        assertEquals(2, result.size)
        // start: distance=0 → x=0; speed=50 (min) → y=canvasHeight (max y due to flip)
        assertEquals(0f, result[0].first, PROJECT_TOLERANCE)
        assertEquals(CANVAS_HEIGHT, result[0].second, PROJECT_TOLERANCE)
        // end: distance=max → x=canvasWidth; speed=200 (max) → y=0
        assertEquals(CANVAS_WIDTH, result[1].first, PROJECT_TOLERANCE)
        assertEquals(0f, result[1].second, PROJECT_TOLERANCE)
    }

    @Test
    fun midpoint_sample_projects_to_canvas_center_so_linear_projection_doesnt_warp_the_middle_of_the_lap() {
        val start = SpeedTraceSample(distanceMeters = 0.0, speedKmh = 100.0)
        val mid = SpeedTraceSample(distanceMeters = 500.0, speedKmh = 150.0)
        val end = SpeedTraceSample(distanceMeters = 1_000.0, speedKmh = 200.0)

        val result = SpeedTraceProjector.project(
            samples = listOf(start, mid, end),
            canvasWidthPx = CANVAS_WIDTH,
            canvasHeightPx = CANVAS_HEIGHT,
        )

        assertEquals(3, result.size)
        assertEquals(CANVAS_WIDTH / 2f, result[1].first, PROJECT_TOLERANCE)
        assertEquals(CANVAS_HEIGHT / 2f, result[1].second, PROJECT_TOLERANCE)
    }

    @Test
    fun constant_distance_collapses_x_to_canvas_center_so_a_stationary_sampler_doesnt_pile_into_a_corner() {
        // Defensive: if for some weird reason all samples emit at
        // the same distance (sampler running while car stopped,
        // distance integrator stuck), the projection must not
        // divide by zero — x collapses to center.
        val stationary = listOf(
            SpeedTraceSample(distanceMeters = 250.0, speedKmh = 0.0),
            SpeedTraceSample(distanceMeters = 250.0, speedKmh = 50.0),
        )

        val result = SpeedTraceProjector.project(
            samples = stationary,
            canvasWidthPx = CANVAS_WIDTH,
            canvasHeightPx = CANVAS_HEIGHT,
        )

        assertEquals(2, result.size)
        result.forEach { (x, _) ->
            assertEquals(CANVAS_WIDTH / 2f, x, PROJECT_TOLERANCE)
        }
    }

    @Test
    fun constant_speed_collapses_y_to_canvas_center_so_a_cruise_control_lap_renders_as_a_horizontal_line() {
        val cruise = listOf(
            SpeedTraceSample(distanceMeters = 0.0, speedKmh = 100.0),
            SpeedTraceSample(distanceMeters = 500.0, speedKmh = 100.0),
            SpeedTraceSample(distanceMeters = 1_000.0, speedKmh = 100.0),
        )

        val result = SpeedTraceProjector.project(
            samples = cruise,
            canvasWidthPx = CANVAS_WIDTH,
            canvasHeightPx = CANVAS_HEIGHT,
        )

        assertEquals(3, result.size)
        result.forEach { (_, y) ->
            assertEquals(CANVAS_HEIGHT / 2f, y, PROJECT_TOLERANCE)
        }
        // X still spreads normally
        assertEquals(0f, result[0].first, PROJECT_TOLERANCE)
        assertEquals(CANVAS_WIDTH, result[2].first, PROJECT_TOLERANCE)
    }

    @Test
    fun projection_preserves_sample_order_so_a_trace_is_drawn_in_capture_sequence_not_shuffled() {
        // The Compose Canvas strokes a Path that connects samples in
        // order. Projection MUST preserve order or the trace would
        // zigzag chaotically.
        val samples = listOf(
            SpeedTraceSample(distanceMeters = 0.0, speedKmh = 80.0),
            SpeedTraceSample(distanceMeters = 100.0, speedKmh = 120.0),
            SpeedTraceSample(distanceMeters = 200.0, speedKmh = 100.0),
            SpeedTraceSample(distanceMeters = 300.0, speedKmh = 150.0),
        )

        val result = SpeedTraceProjector.project(
            samples = samples,
            canvasWidthPx = CANVAS_WIDTH,
            canvasHeightPx = CANVAS_HEIGHT,
        )

        // X is monotonic in input order
        for (i in 1 until result.size) {
            assertTrue(
                "expected x[$i] > x[${i - 1}] but got ${result[i].first} vs ${result[i - 1].first}",
                result[i].first > result[i - 1].first,
            )
        }
    }
}
