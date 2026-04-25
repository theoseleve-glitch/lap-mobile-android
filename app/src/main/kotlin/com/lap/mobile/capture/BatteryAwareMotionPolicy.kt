package com.lap.mobile.capture

private const val LOW_BATTERY_THRESHOLD_PERCENT: Int = 20
private const val UNKNOWN_BATTERY_BOUNDARY: Int = 0

/**
 * Pure decision policy for IMU sampling rate as a function of battery
 * level (T073). Below 20 % we drop to [MotionCaptureConfig.TWENTY_FIVE_HERTZ]
 * so a fading battery doesn't wipe out the rest of a track session;
 * at 20 % or above we stay at [MotionCaptureConfig.FIFTY_HERTZ].
 *
 * Threshold is strict (`<`) so a phone reporting "20 %" stays full-
 * speed — the integer-percent resolution gives implicit hysteresis
 * against flapping. Negative readings (Android's `BatteryManager`
 * returns `Int.MIN_VALUE` when capacity is unavailable) are treated
 * as "unknown" and default to the un-throttled rate; preflight (T091)
 * is the right place to surface a battery service that's actually
 * broken, not this throttler.
 *
 * The policy itself doesn't observe `BatteryManager` — the consumer
 * (T076 SessionCoordinator wiring) listens for `ACTION_BATTERY_CHANGED`
 * and re-invokes [configFor] on each tick, then re-registers the
 * SensorManager listener with the resulting rate.
 */
class BatteryAwareMotionPolicy {
    fun configFor(batteryPercent: Int): MotionCaptureConfig = when {
        batteryPercent < UNKNOWN_BATTERY_BOUNDARY -> MotionCaptureConfig.FIFTY_HERTZ
        batteryPercent < LOW_BATTERY_THRESHOLD_PERCENT -> MotionCaptureConfig.TWENTY_FIVE_HERTZ
        else -> MotionCaptureConfig.FIFTY_HERTZ
    }
}
