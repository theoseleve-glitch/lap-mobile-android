package com.lap.mobile.capture

import kotlinx.coroutines.flow.Flow

/**
 * Provider-agnostic source of [LocationSample]s. The production
 * implementation ([FusedLocationDataSource]) wraps Google Play
 * services' `FusedLocationProviderClient`; tests inject a fake. This
 * seam is what lets [LocationCaptureService] stay JVM-testable.
 */
fun interface LocationDataSource {
    fun samples(): Flow<LocationSample>
}
