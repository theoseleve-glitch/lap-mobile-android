package com.lap.mobile.capture

import org.junit.Assert.assertEquals
import org.junit.Test

private const val BATTERY_LOW_19_PERCENT: Int = 19
private const val BATTERY_BOUNDARY_20_PERCENT: Int = 20
private const val BATTERY_JUST_OVER_THRESHOLD_21_PERCENT: Int = 21
private const val BATTERY_EMPTY_0_PERCENT: Int = 0
private const val BATTERY_FULL_100_PERCENT: Int = 100
private const val BATTERY_PROPERTY_NOT_AVAILABLE: Int = Int.MIN_VALUE
private const val BATTERY_NEGATIVE_SENTINEL: Int = -1

/**
 * Battery-aware throttling for the IMU stream (T073). Threshold is
 * strict (`<` 20 %), so a phone reporting exactly "20 %" stays at the
 * full 50 Hz rate — the 1 %-integer-resolution gives implicit
 * hysteresis against flapping when the battery ticks across the
 * boundary mid-session.
 *
 * Negative readings (Android's `BatteryManager` returns `Int.MIN_VALUE`
 * when the capacity property isn't available) are treated as "unknown"
 * and default to the un-throttled 50 Hz config — racing telemetry
 * shouldn't silently halve its sample rate just because the battery
 * service hiccupped.
 */
class BatteryAwareMotionPolicyTest {
    private val policy = BatteryAwareMotionPolicy()

    @Test
    fun battery_below_threshold_returns_twenty_five_hertz() {
        assertEquals(
            MotionCaptureConfig.TWENTY_FIVE_HERTZ,
            policy.configFor(BATTERY_LOW_19_PERCENT),
        )
    }

    @Test
    fun battery_exactly_at_threshold_returns_fifty_hertz() {
        // Strict `<` — 20 % belongs to "still full speed".
        assertEquals(
            MotionCaptureConfig.FIFTY_HERTZ,
            policy.configFor(BATTERY_BOUNDARY_20_PERCENT),
        )
    }

    @Test
    fun battery_just_over_threshold_returns_fifty_hertz() {
        assertEquals(
            MotionCaptureConfig.FIFTY_HERTZ,
            policy.configFor(BATTERY_JUST_OVER_THRESHOLD_21_PERCENT),
        )
    }

    @Test
    fun battery_at_zero_returns_twenty_five_hertz() {
        assertEquals(
            MotionCaptureConfig.TWENTY_FIVE_HERTZ,
            policy.configFor(BATTERY_EMPTY_0_PERCENT),
        )
    }

    @Test
    fun battery_at_one_hundred_returns_fifty_hertz() {
        assertEquals(
            MotionCaptureConfig.FIFTY_HERTZ,
            policy.configFor(BATTERY_FULL_100_PERCENT),
        )
    }

    @Test
    fun battery_property_not_available_returns_fifty_hertz() {
        // Android's BatteryManager returns Int.MIN_VALUE when the property is
        // unavailable. We default to un-throttled rather than throttle blindly
        // on a sensor-service hiccup.
        assertEquals(
            MotionCaptureConfig.FIFTY_HERTZ,
            policy.configFor(BATTERY_PROPERTY_NOT_AVAILABLE),
        )
    }

    @Test
    fun negative_sentinel_returns_fifty_hertz() {
        // Other "I don't know" sentinels — anything <0 falls under unknown.
        assertEquals(
            MotionCaptureConfig.FIFTY_HERTZ,
            policy.configFor(BATTERY_NEGATIVE_SENTINEL),
        )
    }

    @Test
    fun twenty_five_hertz_config_is_forty_thousand_micros_per_sample() {
        // 25 Hz = 1 s / 25 = 40 ms = 40_000 µs. Locked here so a future tier
        // change ("hi-power 100 Hz") doesn't accidentally redefine 25 Hz.
        assertEquals(40_000, MotionCaptureConfig.TWENTY_FIVE_HERTZ.samplingPeriodMicros)
    }
}
