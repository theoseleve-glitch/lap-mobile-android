package com.lap.mobile.dashboard

/**
 * Pure projection function: G-G samples → canvas coordinates (T197).
 *
 * Bipolar projection — input ±[maxG] maps to canvas edges, with
 * origin (0, 0) at canvas center:
 *   - x = centerX + (latAccelG / maxG) * halfCanvasWidth
 *   - y = centerY - (lonAccelG / maxG) * halfCanvasHeight
 *
 * Y is inverted (high lonAccel near canvas top) because canvas Y
 * grows down but +lonAccel = forward acceleration. Drivers expect
 * "throttle up, brake down" on the diagram.
 *
 * Samples beyond ±[maxG] are clamped to canvas edges via
 * [Double.coerceIn] — a calibration spike or a curb impact must
 * not render off-screen. The dot rendered at the edge tells the
 * driver "you saturated the chosen axis range" (which is
 * actionable — consider widening maxG).
 *
 * Default [maxG] = 2.0 covers most street-tire grip envelopes.
 * Race-tire setups can pass 2.5 or 3.0; the kernel doesn't care
 * what the chosen bound is, just clamps relative to it.
 *
 * Performance: O(N). A 2-minute lap at 50Hz IMU → ~6000 samples.
 * Per-frame projection cost is negligible.
 */
object GgProjector {
    fun project(
        samples: List<GgSample>,
        canvasWidthPx: Float,
        canvasHeightPx: Float,
        maxG: Double = DEFAULT_MAX_G,
    ): List<Pair<Float, Float>> {
        if (samples.isEmpty()) return emptyList()
        val centerX = canvasWidthPx / CENTER_DIVISOR
        val centerY = canvasHeightPx / CENTER_DIVISOR
        val halfW = canvasWidthPx / CENTER_DIVISOR
        val halfH = canvasHeightPx / CENTER_DIVISOR
        return samples.map { sample ->
            val latNorm = (sample.latAccelG / maxG).coerceIn(NORM_MIN, NORM_MAX)
            val lonNorm = (sample.lonAccelG / maxG).coerceIn(NORM_MIN, NORM_MAX)
            val x = centerX + (latNorm * halfW).toFloat()
            val y = centerY - (lonNorm * halfH).toFloat()
            x to y
        }
    }

    const val DEFAULT_MAX_G: Double = 2.0
    private const val CENTER_DIVISOR: Float = 2f
    private const val NORM_MIN: Double = -1.0
    private const val NORM_MAX: Double = 1.0
}
