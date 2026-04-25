package com.lap.mobile.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CANVAS_WIDTH: Float = 200f
private const val CANVAS_HEIGHT: Float = 100f
private const val PROJECT_TOLERANCE: Float = 0.01f

/**
 * Locks the contract for [TrackProjector] (T195) — the pure
 * function that turns a GPS trail (List<GpsPoint>) into canvas
 * coordinates the [com.lap.mobile.ui.dashboard.TrackMap] Compose
 * Canvas can stroke as a breadcrumb.
 *
 * Why a kernel split: the projection math (lat/lon → canvas px)
 * is the part that determines whether the track looks right.
 * Bugs here render as visually-broken-tracks; JVM-testing the
 * math means a Composable refactor can't drift the result.
 *
 * Projection model: linear bounding-box fit. The trail's lat/lon
 * extremes define a bounding rectangle; that rectangle stretches
 * to fill the canvas. Y is inverted because canvas Y grows down
 * but latitude grows north. This is **not** a cartographically
 * correct projection (a 5km-wide track at 60° latitude would look
 * stretched east-west); for v1 the breadcrumb shape is more
 * important than absolute aspect-ratio fidelity. A future
 * cosine-corrected variant can land if user feedback says
 * "tracks look squashed".
 *
 * Performance acceptance — "performant at 10Hz updates" — is
 * algorithmic: project() is O(N) where N is point count. For a
 * 10-minute lap at 10Hz that's ~6000 points; per-frame
 * projection cost is ~6000 simple subtraction/multiply ops, well
 * under the 16ms/frame budget. Live device verification is
 * [PENDING: device-required].
 */
class TrackProjectorTest {
    @Test
    fun empty_trail_projects_to_empty_so_pre_session_canvas_draws_nothing_not_a_phantom_dot() {
        val result = TrackProjector.project(
            points = emptyList(),
            canvasWidthPx = CANVAS_WIDTH,
            canvasHeightPx = CANVAS_HEIGHT,
        )

        assertTrue("expected empty projection, got: $result", result.isEmpty())
    }

    @Test
    fun single_point_projects_to_canvas_center_so_first_GPS_fix_draws_a_dot_in_the_middle_not_in_a_corner() {
        val result = TrackProjector.project(
            points = listOf(GpsPoint(latitudeDeg = 45.0, longitudeDeg = 5.0)),
            canvasWidthPx = CANVAS_WIDTH,
            canvasHeightPx = CANVAS_HEIGHT,
        )

        assertEquals(1, result.size)
        assertEquals(CANVAS_WIDTH / 2f, result[0].first, PROJECT_TOLERANCE)
        assertEquals(CANVAS_HEIGHT / 2f, result[0].second, PROJECT_TOLERANCE)
    }

    @Test
    fun all_points_at_same_lat_lon_project_to_canvas_center_so_a_stationary_GPS_fix_doesnt_jitter_to_a_corner() {
        // Defensive: a stuck GPS could emit the same fix multiple
        // times. The projection must not divide by a zero range
        // and produce NaN — all same-coordinate points map to
        // canvas center.
        val sameSpot = List(5) { GpsPoint(latitudeDeg = 45.0, longitudeDeg = 5.0) }

        val result = TrackProjector.project(
            points = sameSpot,
            canvasWidthPx = CANVAS_WIDTH,
            canvasHeightPx = CANVAS_HEIGHT,
        )

        assertEquals(5, result.size)
        result.forEach { (x, y) ->
            assertEquals(CANVAS_WIDTH / 2f, x, PROJECT_TOLERANCE)
            assertEquals(CANVAS_HEIGHT / 2f, y, PROJECT_TOLERANCE)
        }
    }

    @Test
    fun two_diagonal_points_project_to_opposite_canvas_corners_so_bounding_box_stretches_to_fill_canvas() {
        // Headline T195 invariant: trail extremes map to canvas
        // extremes. SW corner (min lat, min lon) goes to canvas
        // bottom-left; NE corner (max lat, max lon) goes to canvas
        // top-right (Y inverted because canvas Y grows down).
        val sw = GpsPoint(latitudeDeg = 45.0, longitudeDeg = 5.0)
        val ne = GpsPoint(latitudeDeg = 46.0, longitudeDeg = 6.0)

        val result = TrackProjector.project(
            points = listOf(sw, ne),
            canvasWidthPx = CANVAS_WIDTH,
            canvasHeightPx = CANVAS_HEIGHT,
        )

        assertEquals(2, result.size)
        // SW point → x=0 (min lon), y=canvasHeight (min lat → max y due to flip)
        assertEquals(0f, result[0].first, PROJECT_TOLERANCE)
        assertEquals(CANVAS_HEIGHT, result[0].second, PROJECT_TOLERANCE)
        // NE point → x=canvasWidth (max lon), y=0 (max lat → min y)
        assertEquals(CANVAS_WIDTH, result[1].first, PROJECT_TOLERANCE)
        assertEquals(0f, result[1].second, PROJECT_TOLERANCE)
    }

    @Test
    fun midpoint_projects_to_canvas_center_so_linear_projection_doesnt_warp_the_middle() {
        val sw = GpsPoint(latitudeDeg = 45.0, longitudeDeg = 5.0)
        val ne = GpsPoint(latitudeDeg = 47.0, longitudeDeg = 7.0)
        val mid = GpsPoint(latitudeDeg = 46.0, longitudeDeg = 6.0)

        val result = TrackProjector.project(
            points = listOf(sw, mid, ne),
            canvasWidthPx = CANVAS_WIDTH,
            canvasHeightPx = CANVAS_HEIGHT,
        )

        assertEquals(3, result.size)
        // Mid point — lon is 50% between min/max, lat is 50%
        assertEquals(CANVAS_WIDTH / 2f, result[1].first, PROJECT_TOLERANCE)
        assertEquals(CANVAS_HEIGHT / 2f, result[1].second, PROJECT_TOLERANCE)
    }

    @Test
    fun east_west_only_trail_collapses_y_to_canvas_center_so_a_straight_horizontal_line_renders_horizontally() {
        // Lat range is 0; lon range is non-zero. y must default to
        // center (not NaN); x must spread across canvas.
        val west = GpsPoint(latitudeDeg = 45.0, longitudeDeg = 5.0)
        val east = GpsPoint(latitudeDeg = 45.0, longitudeDeg = 6.0)

        val result = TrackProjector.project(
            points = listOf(west, east),
            canvasWidthPx = CANVAS_WIDTH,
            canvasHeightPx = CANVAS_HEIGHT,
        )

        assertEquals(2, result.size)
        assertEquals(0f, result[0].first, PROJECT_TOLERANCE)
        assertEquals(CANVAS_HEIGHT / 2f, result[0].second, PROJECT_TOLERANCE)
        assertEquals(CANVAS_WIDTH, result[1].first, PROJECT_TOLERANCE)
        assertEquals(CANVAS_HEIGHT / 2f, result[1].second, PROJECT_TOLERANCE)
    }

    @Test
    fun north_south_only_trail_collapses_x_to_canvas_center_so_a_straight_vertical_line_renders_vertically() {
        val south = GpsPoint(latitudeDeg = 45.0, longitudeDeg = 5.0)
        val north = GpsPoint(latitudeDeg = 46.0, longitudeDeg = 5.0)

        val result = TrackProjector.project(
            points = listOf(south, north),
            canvasWidthPx = CANVAS_WIDTH,
            canvasHeightPx = CANVAS_HEIGHT,
        )

        assertEquals(2, result.size)
        assertEquals(CANVAS_WIDTH / 2f, result[0].first, PROJECT_TOLERANCE)
        assertEquals(CANVAS_HEIGHT, result[0].second, PROJECT_TOLERANCE)
        assertEquals(CANVAS_WIDTH / 2f, result[1].first, PROJECT_TOLERANCE)
        assertEquals(0f, result[1].second, PROJECT_TOLERANCE)
    }

    @Test
    fun projection_preserves_point_order_so_a_breadcrumb_is_drawn_in_capture_sequence_not_shuffled() {
        // The Compose Canvas strokes a Path that connects points
        // in order. Projection MUST preserve order or the
        // breadcrumb would zigzag chaotically.
        val a = GpsPoint(latitudeDeg = 45.0, longitudeDeg = 5.0)
        val b = GpsPoint(latitudeDeg = 45.5, longitudeDeg = 5.5)
        val c = GpsPoint(latitudeDeg = 46.0, longitudeDeg = 6.0)

        val result = TrackProjector.project(
            points = listOf(a, b, c),
            canvasWidthPx = CANVAS_WIDTH,
            canvasHeightPx = CANVAS_HEIGHT,
        )

        // a is SW corner, c is NE corner, b is in the middle
        assertTrue("first point must be at min x", result[0].first < result[1].first)
        assertTrue("second point must be x-mid", result[1].first < result[2].first)
        assertTrue("first point must be at max y (inverted lat)", result[0].second > result[1].second)
        assertTrue("second point must be y-mid", result[1].second > result[2].second)
    }
}
