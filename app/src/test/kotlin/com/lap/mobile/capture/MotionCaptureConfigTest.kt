package com.lap.mobile.capture

import org.junit.Assert.assertEquals
import org.junit.Test

private const val MICROS_PER_SAMPLE_AT_50HZ: Int = 20_000

/**
 * Lock for the 50 Hz capture rate T068 calls out. SensorManager takes
 * the rate in microseconds, so 50 Hz = 20_000 µs/sample. Flipping
 * this number silently changes the rate the IMU pipeline produces,
 * which would skew downstream cadence assumptions (T071 orchestrator
 * sample-merging, T079 buffer-flush sizing).
 */
class MotionCaptureConfigTest {
    @Test
    fun fifty_hertz_is_twenty_thousand_microseconds_per_sample() {
        assertEquals(
            MICROS_PER_SAMPLE_AT_50HZ,
            MotionCaptureConfig.FIFTY_HERTZ.samplingPeriodMicros,
        )
    }
}
