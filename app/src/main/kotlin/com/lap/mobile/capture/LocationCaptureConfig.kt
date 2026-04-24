package com.lap.mobile.capture

private const val MILLIS_PER_SAMPLE_AT_10HZ: Long = 100L

/**
 * Capture rate + accuracy spec handed to [FusedLocationDataSource]
 * when configuring the underlying `LocationRequest`. T061's bar is
 * "FusedLocationProviderClient at high-accuracy, 10 Hz" — encoded
 * here as [HIGH_ACCURACY_10HZ] so the DI module can wire the same
 * config the recording-state-machine (T075) and capture orchestrator
 * (T071) will reference by name.
 */
data class LocationCaptureConfig(
    val intervalMillis: Long,
    val accuracyMode: LocationAccuracyMode,
) {
    companion object {
        val HIGH_ACCURACY_10HZ: LocationCaptureConfig = LocationCaptureConfig(
            intervalMillis = MILLIS_PER_SAMPLE_AT_10HZ,
            accuracyMode = LocationAccuracyMode.HIGH_ACCURACY,
        )
    }
}
