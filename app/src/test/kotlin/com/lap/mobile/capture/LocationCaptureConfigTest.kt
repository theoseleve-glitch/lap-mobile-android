package com.lap.mobile.capture

import org.junit.Assert.assertEquals
import org.junit.Test

private const val EXPECTED_INTERVAL_MS_FOR_10HZ: Long = 100L

/**
 * Lock for the high-accuracy 10 Hz capture config T061 calls out
 * explicitly. 10 Hz = one sample every 100 ms; flipping that to a
 * different number would silently change the sample rate the server
 * sees, breaking the assumption baked into Block D's downstream
 * tasks (T065 mapping, T079 buffer-flush sizing).
 */
class LocationCaptureConfigTest {
    @Test
    fun ten_hertz_high_accuracy_uses_one_hundred_milli_interval() {
        assertEquals(
            EXPECTED_INTERVAL_MS_FOR_10HZ,
            LocationCaptureConfig.HIGH_ACCURACY_10HZ.intervalMillis,
        )
    }

    @Test
    fun ten_hertz_high_accuracy_requests_high_accuracy_mode() {
        assertEquals(
            LocationAccuracyMode.HIGH_ACCURACY,
            LocationCaptureConfig.HIGH_ACCURACY_10HZ.accuracyMode,
        )
    }
}
