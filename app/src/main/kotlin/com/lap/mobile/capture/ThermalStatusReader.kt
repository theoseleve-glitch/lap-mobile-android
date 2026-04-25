package com.lap.mobile.capture

/**
 * Live thermal-state reading for [ThermalStateCheck] (T095). Returns
 * the integer code defined by `android.os.PowerManager` —
 * `THERMAL_STATUS_NONE` (0) through `THERMAL_STATUS_SHUTDOWN` (6),
 * with negatives reserved for unknown-sentinel readings the same way
 * [ThermalAwareCapturePolicy] tolerates them.
 */
fun interface ThermalStatusReader {
    suspend fun currentStatus(): Int
}
