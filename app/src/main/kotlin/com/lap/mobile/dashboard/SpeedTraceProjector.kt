package com.lap.mobile.dashboard

/**
 * Pure projection function: speed-over-distance series → canvas
 * coordinates (T196).
 *
 * Linear bounds-fit projection — same shape as T195's
 * [TrackProjector] but operating on (distance, speed) pairs:
 *   - X = (distance - minDistance) / distanceRange * canvasWidth
 *   - Y = (maxSpeed - speed) / speedRange * canvasHeight
 *
 * Y is inverted (high speed near canvas top) because canvas Y grows
 * down but speed grows up — matches every chart-convention drivers
 * see in live-timing tools.
 *
 * Auto-fit bounds: the trace's own (min, max) per axis define the
 * stretch. Last-lap-only scope means we don't need a fixed Y axis
 * (e.g., "always 0–250 km/h"); a future multi-lap overlay can swap
 * to a fixed axis without changing this call site.
 *
 * Performance: O(N) where N is sample count. A 2-minute lap at 10Hz
 * is ~1200 samples, well under the 16ms/frame budget.
 *
 * Defensive cases:
 *   - Empty input → empty output.
 *   - Single sample or all-same-distance-and-speed → all map to
 *     canvas center (no division-by-zero NaN).
 *   - Constant distance (sampler running while car stopped, distance
 *     integrator stuck) → x collapses to canvas center; y spreads
 *     normally.
 *   - Constant speed (cruise control hypothetical) → y collapses to
 *     canvas center; x spreads normally — renders as a horizontal
 *     line.
 *   - Order preserved (chart drawn in capture sequence).
 */
object SpeedTraceProjector {
    fun project(
        samples: List<SpeedTraceSample>,
        canvasWidthPx: Float,
        canvasHeightPx: Float,
    ): List<Pair<Float, Float>> {
        if (samples.isEmpty()) return emptyList()
        val centerX = canvasWidthPx / CENTER_DIVISOR
        val centerY = canvasHeightPx / CENTER_DIVISOR
        val minDistance = samples.minOf { it.distanceMeters }
        val maxDistance = samples.maxOf { it.distanceMeters }
        val minSpeed = samples.minOf { it.speedKmh }
        val maxSpeed = samples.maxOf { it.speedKmh }
        val distanceRange = maxDistance - minDistance
        val speedRange = maxSpeed - minSpeed
        return samples.map { sample ->
            val x =
                if (distanceRange == 0.0) {
                    centerX
                } else {
                    (((sample.distanceMeters - minDistance) / distanceRange) * canvasWidthPx).toFloat()
                }
            val y =
                if (speedRange == 0.0) {
                    centerY
                } else {
                    (((maxSpeed - sample.speedKmh) / speedRange) * canvasHeightPx).toFloat()
                }
            x to y
        }
    }

    private const val CENTER_DIVISOR: Float = 2f
}
