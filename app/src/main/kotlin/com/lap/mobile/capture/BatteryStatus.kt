package com.lap.mobile.capture

/**
 * Live battery level reading for [BatteryLevelCheck] (T094) and
 * (later) the dashboard. Returns the integer percent in `[0, 100]`,
 * or a negative sentinel when the platform reports an unknown level
 * (e.g., transient `BATTERY_PROPERTY_CAPACITY` failure on cold boot).
 *
 * The Android impl reads `BatteryManager.BATTERY_PROPERTY_CAPACITY`
 * synchronously — it's a system-service field, not a broadcast, so
 * `suspend` is precautionary not strictly necessary.
 */
fun interface BatteryStatus {
    suspend fun levelPercent(): Int
}
