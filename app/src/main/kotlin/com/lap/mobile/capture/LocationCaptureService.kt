package com.lap.mobile.capture

import kotlinx.coroutines.flow.Flow

/**
 * Public capture API for the recording pipeline (T076 wires this into
 * `SessionCoordinator`). The service is intentionally a thin pass-
 * through so the platform-specific bits — request building, callback
 * plumbing, permission errors — stay in [FusedLocationDataSource]
 * where instrumented tests can exercise them on a real device (T083).
 *
 * Each call to [samples] returns a fresh [Flow]; collectors do not
 * share state.
 */
class LocationCaptureService(
    private val source: LocationDataSource,
) {
    fun samples(): Flow<LocationSample> = source.samples()
}
