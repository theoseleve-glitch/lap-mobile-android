package com.lap.mobile.capture

private const val ALPHA_LOWER_EXCLUSIVE: Float = 0.0f
private const val ALPHA_UPPER_INCLUSIVE: Float = 1.0f

/**
 * Default smoothing factor — moderate first-order IIR low-pass on the
 * accelerometer (T070). Picked for road-noise rejection without
 * smearing transient events (curb hits, brake bites). Configurable
 * per [AccelerometerLowPassFilter] constructor argument so a tuning
 * task or A/B can override.
 */
private const val DEFAULT_ALPHA: Float = 0.2f

/**
 * IIR exponential moving average on the accelerometer:
 *
 * ```
 * y[n] = α·x[n] + (1 − α)·y[n−1]
 * ```
 *
 * Per-axis state; the gyroscope is intentionally untouched (different
 * noise spectrum). [filter] is the per-sample API; [applyTo] is the
 * Flow-friendly variant that pattern-matches on the sealed
 * [MotionSample] subtype.
 *
 * Constructor rejects α ∉ (0, 1] — α = 0 makes the filter dead-stuck
 * at the first sample forever, α > 1 amplifies (not a low-pass).
 * α = 1 is allowed and means "no smoothing", which makes the filter
 * a runtime-disable knob without needing a separate enabled flag.
 *
 * Stateful: not thread-safe. The capture pipeline runs on a single
 * dispatcher per ADR-007's "no on-device fusion" rule, so callers
 * never share an instance across threads.
 */
class AccelerometerLowPassFilter(
    private val alpha: Float = DEFAULT_ALPHA,
) {
    init {
        require(alpha > ALPHA_LOWER_EXCLUSIVE && alpha <= ALPHA_UPPER_INCLUSIVE) {
            "alpha must be in (0, 1], got $alpha"
        }
    }

    private var lastFilteredX: Float? = null
    private var lastFilteredY: Float? = null
    private var lastFilteredZ: Float? = null

    fun filter(sample: MotionSample.Accelerometer): MotionSample.Accelerometer {
        val newX = blend(sample.xMps2, lastFilteredX)
        val newY = blend(sample.yMps2, lastFilteredY)
        val newZ = blend(sample.zMps2, lastFilteredZ)
        lastFilteredX = newX
        lastFilteredY = newY
        lastFilteredZ = newZ
        return sample.copy(xMps2 = newX, yMps2 = newY, zMps2 = newZ)
    }

    fun applyTo(sample: MotionSample): MotionSample = when (sample) {
        is MotionSample.Accelerometer -> filter(sample)
        is MotionSample.Gyroscope -> sample
    }

    private fun blend(input: Float, previous: Float?): Float =
        if (previous == null) input else alpha * input + (ALPHA_UPPER_INCLUSIVE - alpha) * previous
}
