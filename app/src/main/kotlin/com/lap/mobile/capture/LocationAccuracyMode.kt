package com.lap.mobile.capture

/**
 * Provider-agnostic accuracy mode. [FusedLocationDataSource] maps each
 * value onto the corresponding `com.google.android.gms.location.Priority`
 * constant — keeping the testable [LocationCaptureConfig] free of
 * play-services types so JVM unit tests don't drag in the Google
 * Play services SDK.
 */
enum class LocationAccuracyMode {
    HIGH_ACCURACY,
    BALANCED,
    LOW_POWER,
}
