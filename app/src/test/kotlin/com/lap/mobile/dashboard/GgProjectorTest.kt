package com.lap.mobile.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CANVAS_SIDE: Float = 200f
private const val PROJECT_TOLERANCE: Float = 0.01f
private const val MAX_G: Double = 2.0

/**
 * Locks the contract for [GgProjector] (T197) — the pure function
 * that turns acceleration samples (lateral, longitudinal in g-units)
 * into canvas coordinates the
 * [com.lap.mobile.ui.dashboard.GgDiagram] Compose Canvas can render
 * as a friction-circle scatter.
 *
 * Why a kernel split (projector separate from the Compose chart):
 * G-G interpretation depends on a stable axis convention. Bugs here
 * (e.g., Y not inverted, axes swapped) render as visually-broken
 * diagrams that a driver would silently misread.
 *
 * Axis convention:
 *   - X axis: lateral acceleration. +X = right-cornering (right
 *     turn pulls car right → accelerometer feels rightward force);
 *     -X = left-cornering.
 *   - Y axis: longitudinal acceleration. +Y = forward acceleration
 *     (throttle on, car pulls forward); -Y = braking. Canvas Y is
 *     inverted (canvas Y grows down) so positive lonAccel maps to
 *     canvas TOP — matches the driver's mental model "throttle up,
 *     brake down".
 *   - Origin (0g, 0g) maps to canvas center.
 *
 * Bipolar projection: ±[maxG] maps to canvas edges. Samples beyond
 * ±maxG are clamped to canvas edge (rendered as if at the friction
 * limit) rather than overflowing off-screen.
 *
 * Defensive cases (locked here):
 *   - Empty input → empty output.
 *   - Origin sample → canvas center.
 *   - ±maxG samples → canvas edges (centerX ± halfW, etc).
 *   - Beyond ±maxG → clamped to canvas edge (not off-screen).
 *   - Order preserved (caller may want to color recent samples
 *     differently from old ones).
 */
class GgProjectorTest {
    @Test
    fun empty_input_projects_to_empty_so_pre_session_canvas_draws_nothing() {
        val result = GgProjector.project(
            samples = emptyList(),
            canvasWidthPx = CANVAS_SIDE,
            canvasHeightPx = CANVAS_SIDE,
            maxG = MAX_G,
        )

        assertTrue("expected empty projection, got: $result", result.isEmpty())
    }

    @Test
    fun origin_sample_projects_to_canvas_center_so_a_stationary_car_dot_sits_at_the_friction_circle_center() {
        val result = GgProjector.project(
            samples = listOf(GgSample(latAccelG = 0.0, lonAccelG = 0.0)),
            canvasWidthPx = CANVAS_SIDE,
            canvasHeightPx = CANVAS_SIDE,
            maxG = MAX_G,
        )

        assertEquals(1, result.size)
        assertEquals(CANVAS_SIDE / 2f, result[0].first, PROJECT_TOLERANCE)
        assertEquals(CANVAS_SIDE / 2f, result[0].second, PROJECT_TOLERANCE)
    }

    @Test
    fun positive_lat_g_projects_to_right_edge_so_a_right_corner_sample_lands_in_the_right_half() {
        // +maxG laterally → right edge of canvas; lon=0 keeps y at center.
        val result = GgProjector.project(
            samples = listOf(GgSample(latAccelG = MAX_G, lonAccelG = 0.0)),
            canvasWidthPx = CANVAS_SIDE,
            canvasHeightPx = CANVAS_SIDE,
            maxG = MAX_G,
        )

        assertEquals(CANVAS_SIDE, result[0].first, PROJECT_TOLERANCE)
        assertEquals(CANVAS_SIDE / 2f, result[0].second, PROJECT_TOLERANCE)
    }

    @Test
    fun negative_lat_g_projects_to_left_edge_so_a_left_corner_sample_lands_in_the_left_half() {
        val result = GgProjector.project(
            samples = listOf(GgSample(latAccelG = -MAX_G, lonAccelG = 0.0)),
            canvasWidthPx = CANVAS_SIDE,
            canvasHeightPx = CANVAS_SIDE,
            maxG = MAX_G,
        )

        assertEquals(0f, result[0].first, PROJECT_TOLERANCE)
        assertEquals(CANVAS_SIDE / 2f, result[0].second, PROJECT_TOLERANCE)
    }

    @Test
    fun positive_lon_g_to_canvas_top_so_full_throttle_lands_at_top_per_drivers_throttle_up_brake_down_model() {
        // Headline T197 invariant: +Y = forward accel = canvas TOP
        // (Y inverted from canvas down-grows-positive). A driver
        // glancing at the diagram during accel must see the dot
        // climb upward — matches "throttle up, brake down".
        val result = GgProjector.project(
            samples = listOf(GgSample(latAccelG = 0.0, lonAccelG = MAX_G)),
            canvasWidthPx = CANVAS_SIDE,
            canvasHeightPx = CANVAS_SIDE,
            maxG = MAX_G,
        )

        assertEquals(CANVAS_SIDE / 2f, result[0].first, PROJECT_TOLERANCE)
        assertEquals(0f, result[0].second, PROJECT_TOLERANCE)
    }

    @Test
    fun negative_lon_g_to_canvas_bottom_so_braking_lands_at_bottom_per_drivers_throttle_up_brake_down_model() {
        val result = GgProjector.project(
            samples = listOf(GgSample(latAccelG = 0.0, lonAccelG = -MAX_G)),
            canvasWidthPx = CANVAS_SIDE,
            canvasHeightPx = CANVAS_SIDE,
            maxG = MAX_G,
        )

        assertEquals(CANVAS_SIDE / 2f, result[0].first, PROJECT_TOLERANCE)
        assertEquals(CANVAS_SIDE, result[0].second, PROJECT_TOLERANCE)
    }

    @Test
    fun half_g_projects_to_quarter_offset_so_linear_projection_holds_inside_the_friction_circle() {
        // (+maxG/2, +maxG/2) → canvas position (3/4 W, 1/4 H)
        // because half-maxG maps to half-of-half-canvas-width offset.
        val result = GgProjector.project(
            samples = listOf(GgSample(latAccelG = MAX_G / 2.0, lonAccelG = MAX_G / 2.0)),
            canvasWidthPx = CANVAS_SIDE,
            canvasHeightPx = CANVAS_SIDE,
            maxG = MAX_G,
        )

        assertEquals(CANVAS_SIDE * 3f / 4f, result[0].first, PROJECT_TOLERANCE)
        assertEquals(CANVAS_SIDE / 4f, result[0].second, PROJECT_TOLERANCE)
    }

    @Test
    fun beyond_max_g_clamps_to_canvas_edge_so_a_calibration_spike_doesnt_render_off_screen() {
        // A sample beyond ±maxG (3.0g lateral with maxG=2.0) means
        // either tire grip exceeded the chosen axis range OR the
        // accelerometer spiked from a curb impact. Either way the
        // dot must render somewhere visible — clamp to the canvas
        // edge rather than letting it draw off-screen.
        val result = GgProjector.project(
            samples = listOf(GgSample(latAccelG = 3.0, lonAccelG = 0.0)),
            canvasWidthPx = CANVAS_SIDE,
            canvasHeightPx = CANVAS_SIDE,
            maxG = MAX_G,
        )

        assertEquals(CANVAS_SIDE, result[0].first, PROJECT_TOLERANCE)
    }

    @Test
    fun beyond_negative_max_g_clamps_to_left_edge_so_extreme_braking_or_left_corner_doesnt_render_off_screen() {
        val result = GgProjector.project(
            samples = listOf(GgSample(latAccelG = -5.0, lonAccelG = -10.0)),
            canvasWidthPx = CANVAS_SIDE,
            canvasHeightPx = CANVAS_SIDE,
            maxG = MAX_G,
        )

        assertEquals(0f, result[0].first, PROJECT_TOLERANCE)
        assertEquals(CANVAS_SIDE, result[0].second, PROJECT_TOLERANCE)
    }

    @Test
    fun projection_preserves_sample_order_so_a_recent_first_caller_can_render_recent_samples_differently() {
        // The Compose Canvas may want to render recent samples in a
        // brighter color (showing where the car is RIGHT NOW vs the
        // historical envelope). Order preservation lets the caller
        // do that without the kernel needing to know.
        val samples = listOf(
            GgSample(latAccelG = -1.0, lonAccelG = -1.0),
            GgSample(latAccelG = 0.0, lonAccelG = 0.0),
            GgSample(latAccelG = 1.0, lonAccelG = 1.0),
        )

        val result = GgProjector.project(
            samples = samples,
            canvasWidthPx = CANVAS_SIDE,
            canvasHeightPx = CANVAS_SIDE,
            maxG = MAX_G,
        )

        // First sample is bottom-left quadrant
        assertTrue(result[0].first < result[1].first)
        assertTrue(result[0].second > result[1].second)
        // Last sample is top-right quadrant
        assertTrue(result[2].first > result[1].first)
        assertTrue(result[2].second < result[1].second)
    }
}
