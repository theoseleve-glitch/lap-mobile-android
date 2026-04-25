package com.lap.mobile.dashboard

/**
 * Pure projection function: GPS trail → canvas coordinates (T195).
 *
 * Bounding-box-fit projection: the trail's lat/lon extremes define
 * a bounding rectangle that stretches to fill the canvas. Y is
 * inverted (canvas Y grows down, latitude grows north). Order is
 * preserved so the [com.lap.mobile.ui.dashboard.TrackMap] Composable
 * can stroke a Path connecting consecutive points without
 * resorting/scanning.
 *
 * NOT cartographically correct: a 5km-wide track at 60° latitude
 * would look stretched east-west under this projection. For v1
 * the breadcrumb shape is more important than absolute aspect-ratio
 * fidelity (drivers recognize their track from the shape of corners,
 * not from pixel-perfect proportions). A future cosine-corrected
 * variant can land if user feedback says "tracks look squashed".
 *
 * Performance: O(N) where N is point count. For a 10-minute lap at
 * 10Hz that's ~6000 points; per-frame projection cost is ~6000
 * simple subtraction/multiply ops, well under the 16ms/frame budget.
 *
 * Defensive cases:
 *   - Empty input → empty output (caller draws nothing).
 *   - Single point or all-same-point → all map to canvas center
 *     (no division-by-zero NaN; a stationary GPS doesn't jitter
 *     to a corner).
 *   - East-west-only trail (lat range = 0) → all y collapse to
 *     canvas vertical center; x spreads normally.
 *   - North-south-only trail (lon range = 0) → all x collapse to
 *     canvas horizontal center; y spreads normally.
 */
object TrackProjector {
    fun project(points: List<GpsPoint>, canvasWidthPx: Float, canvasHeightPx: Float): List<Pair<Float, Float>> {
        if (points.isEmpty()) return emptyList()
        val centerX = canvasWidthPx / CENTER_DIVISOR
        val centerY = canvasHeightPx / CENTER_DIVISOR
        val minLat = points.minOf { it.latitudeDeg }
        val maxLat = points.maxOf { it.latitudeDeg }
        val minLon = points.minOf { it.longitudeDeg }
        val maxLon = points.maxOf { it.longitudeDeg }
        val latRange = maxLat - minLat
        val lonRange = maxLon - minLon
        return points.map { point ->
            val x =
                if (lonRange == 0.0) {
                    centerX
                } else {
                    (((point.longitudeDeg - minLon) / lonRange) * canvasWidthPx).toFloat()
                }
            val y =
                if (latRange == 0.0) {
                    centerY
                } else {
                    (((maxLat - point.latitudeDeg) / latRange) * canvasHeightPx).toFloat()
                }
            x to y
        }
    }

    private const val CENTER_DIVISOR: Float = 2f
}
