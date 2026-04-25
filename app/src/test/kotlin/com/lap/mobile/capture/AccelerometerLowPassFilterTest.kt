package com.lap.mobile.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

private const val ALPHA_HALF: Float = 0.5f
private const val ALPHA_PASSTHROUGH: Float = 1.0f
private const val ALPHA_LIGHT_SMOOTHING: Float = 0.8f
private const val ALPHA_INVALID_ZERO: Float = 0.0f
private const val ALPHA_INVALID_NEGATIVE: Float = -0.1f
private const val ALPHA_INVALID_OVER_ONE: Float = 1.0001f

private const val FIRST_X: Float = 1.0f
private const val FIRST_Y: Float = 2.0f
private const val FIRST_Z: Float = 9.8f
private const val SECOND_X: Float = 3.0f
private const val SECOND_Y: Float = 4.0f
private const val SECOND_Z: Float = 10.0f
private const val THIRD_X: Float = 5.0f
private const val STEP_CONVERGED_VALUE: Float = 10.0f
private const val FLOAT_TOLERANCE: Float = 0.001f
private const val STEP_CONVERGENCE_ITERATIONS: Int = 50

private const val TS_FIRST_NS: Long = 1_000L
private const val TS_SECOND_NS: Long = 2_000L
private const val GYRO_X_RPS: Float = 0.001f
private const val GYRO_Y_RPS: Float = 0.002f
private const val GYRO_Z_RPS: Float = 0.003f

/**
 * IIR low-pass filter on the accelerometer (T070). Tests cover the
 * EMA recurrence `y[n] = α·x[n] + (1−α)·y[n−1]` plus the boundary
 * behavior the constructor rejects (α ∉ (0, 1]) and the first-sample
 * seeding rule (return input as-is so there's no warm-up tail).
 *
 * The filter is per-axis stateful; tests assert each axis updates
 * independently rather than fusing them.
 */
class AccelerometerLowPassFilterTest {
    @Test
    fun rejects_alpha_of_zero() {
        // α = 0 makes the filter never update — output stuck at first value forever.
        assertThrows(IllegalArgumentException::class.java) {
            AccelerometerLowPassFilter(ALPHA_INVALID_ZERO)
        }
    }

    @Test
    fun rejects_negative_alpha() {
        assertThrows(IllegalArgumentException::class.java) {
            AccelerometerLowPassFilter(ALPHA_INVALID_NEGATIVE)
        }
    }

    @Test
    fun rejects_alpha_over_one() {
        // α > 1 amplifies the input; that's not a low-pass.
        assertThrows(IllegalArgumentException::class.java) {
            AccelerometerLowPassFilter(ALPHA_INVALID_OVER_ONE)
        }
    }

    @Test
    fun accepts_alpha_at_upper_bound_one() {
        // α = 1 is "no smoothing" — legitimate "filter disabled" state.
        AccelerometerLowPassFilter(ALPHA_PASSTHROUGH)
    }

    @Test
    fun first_sample_returns_input_unchanged() {
        val filter = AccelerometerLowPassFilter(ALPHA_HALF)
        val input = accelSample(FIRST_X, FIRST_Y, FIRST_Z, TS_FIRST_NS)

        val output = filter.filter(input)

        // No prior state ⇒ no smoothing to apply on first sample.
        assertEquals(FIRST_X, output.xMps2)
        assertEquals(FIRST_Y, output.yMps2)
        assertEquals(FIRST_Z, output.zMps2)
    }

    @Test
    fun alpha_one_means_no_smoothing_ever() {
        val filter = AccelerometerLowPassFilter(ALPHA_PASSTHROUGH)
        filter.filter(accelSample(FIRST_X, FIRST_Y, FIRST_Z, TS_FIRST_NS))

        val out = filter.filter(accelSample(SECOND_X, SECOND_Y, SECOND_Z, TS_SECOND_NS))

        assertEquals(SECOND_X, out.xMps2)
        assertEquals(SECOND_Y, out.yMps2)
        assertEquals(SECOND_Z, out.zMps2)
    }

    @Test
    fun alpha_half_blends_input_and_previous_filtered_value() {
        val filter = AccelerometerLowPassFilter(ALPHA_HALF)
        filter.filter(accelSample(FIRST_X, FIRST_Y, FIRST_Z, TS_FIRST_NS))

        val out = filter.filter(accelSample(SECOND_X, SECOND_Y, SECOND_Z, TS_SECOND_NS))

        // y = 0.5*new + 0.5*prev_filtered (and prev_filtered == first input)
        assertEquals(0.5f * SECOND_X + 0.5f * FIRST_X, out.xMps2)
        assertEquals(0.5f * SECOND_Y + 0.5f * FIRST_Y, out.yMps2)
        assertEquals(0.5f * SECOND_Z + 0.5f * FIRST_Z, out.zMps2)
    }

    @Test
    fun source_timestamp_is_preserved() {
        val filter = AccelerometerLowPassFilter(ALPHA_HALF)
        filter.filter(accelSample(FIRST_X, FIRST_Y, FIRST_Z, TS_FIRST_NS))

        val out = filter.filter(accelSample(SECOND_X, SECOND_Y, SECOND_Z, TS_SECOND_NS))

        assertEquals(TS_SECOND_NS, out.sourceTimestampNs)
    }

    @Test
    fun axes_are_filtered_independently() {
        val filter = AccelerometerLowPassFilter(ALPHA_HALF)
        // Seed with mismatched axes so a bug fusing axes shows up immediately.
        filter.filter(accelSample(FIRST_X, FIRST_Y, FIRST_Z, TS_FIRST_NS))
        val out1 = filter.filter(accelSample(THIRD_X, FIRST_Y, FIRST_Z, TS_SECOND_NS))

        // X moved (3→4 with α=0.5 averaging from 1), Y and Z stayed at FIRST.
        assertEquals(0.5f * THIRD_X + 0.5f * FIRST_X, out1.xMps2)
        assertEquals(FIRST_Y, out1.yMps2)
        assertEquals(FIRST_Z, out1.zMps2)
    }

    @Test
    fun second_sample_filtered_differs_from_raw_input() {
        // Sanity that α=0.8 is actually filtering (not no-op).
        val filter = AccelerometerLowPassFilter(ALPHA_LIGHT_SMOOTHING)
        filter.filter(accelSample(FIRST_X, FIRST_Y, FIRST_Z, TS_FIRST_NS))

        val out = filter.filter(accelSample(SECOND_X, SECOND_Y, SECOND_Z, TS_SECOND_NS))

        assertNotEquals(SECOND_X, out.xMps2)
    }

    @Test
    fun step_response_converges_to_new_value() {
        val filter = AccelerometerLowPassFilter(ALPHA_HALF)
        filter.filter(accelSample(FIRST_X, FIRST_Y, FIRST_Z, TS_FIRST_NS))

        // Feed STEP_CONVERGED_VALUE constantly — the IIR should converge.
        val step = accelSample(
            STEP_CONVERGED_VALUE,
            STEP_CONVERGED_VALUE,
            STEP_CONVERGED_VALUE,
            TS_SECOND_NS,
        )
        repeat(STEP_CONVERGENCE_ITERATIONS) { filter.filter(step) }
        val finalOut = filter.filter(step)

        assertEquals(STEP_CONVERGED_VALUE, finalOut.xMps2, FLOAT_TOLERANCE)
        assertEquals(STEP_CONVERGED_VALUE, finalOut.yMps2, FLOAT_TOLERANCE)
        assertEquals(STEP_CONVERGED_VALUE, finalOut.zMps2, FLOAT_TOLERANCE)
    }

    @Test
    fun gyroscope_samples_are_passed_through_filter_unchanged_in_flow() {
        val filter = AccelerometerLowPassFilter(ALPHA_HALF)
        val gyro = MotionSample.Gyroscope(
            xRadPerSec = GYRO_X_RPS,
            yRadPerSec = GYRO_Y_RPS,
            zRadPerSec = GYRO_Z_RPS,
            sourceTimestampNs = TS_FIRST_NS,
        )
        // Operator applies filter only to accelerometer subtype.
        val passed: MotionSample = filter.applyTo(gyro)
        assertEquals(gyro, passed)
    }

    private fun accelSample(x: Float, y: Float, z: Float, ts: Long): MotionSample.Accelerometer =
        MotionSample.Accelerometer(xMps2 = x, yMps2 = y, zMps2 = z, sourceTimestampNs = ts)
}
