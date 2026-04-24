package com.lap.mobile.capture

import android.annotation.SuppressLint
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Production [LocationDataSource]. Wraps [FusedLocationProviderClient]
 * in a [callbackFlow] so cancellation of the collector also unsubscribes
 * from the platform — important for [Looper]-bound callbacks the user
 * pays power for until torn down.
 *
 * Permission gating (`ACCESS_FINE_LOCATION` + background) is the
 * caller's job (T062) — we mark `MissingPermission` suppressed
 * because Block E preflight (T087) is the system-level gate.
 *
 * Live stream verification needs an Android device + GPS fix; that's
 * T083's instrumented capture test — JVM tests can only exercise the
 * platform-agnostic [LocationCaptureService] over [LocationDataSource].
 */
class FusedLocationDataSource(
    private val client: FusedLocationProviderClient,
    private val config: LocationCaptureConfig = LocationCaptureConfig.HIGH_ACCURACY_10HZ,
    private val callbackLooper: Looper = Looper.getMainLooper(),
) : LocationDataSource {
    @SuppressLint("MissingPermission")
    override fun samples(): Flow<LocationSample> = callbackFlow {
        val request = LocationRequest.Builder(
            config.accuracyMode.toGmsPriority(),
            config.intervalMillis,
        ).build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { trySend(it.toSample()) }
            }
        }

        client.requestLocationUpdates(request, callback, callbackLooper)
        awaitClose { client.removeLocationUpdates(callback) }
    }
}

private fun LocationAccuracyMode.toGmsPriority(): Int = when (this) {
    LocationAccuracyMode.HIGH_ACCURACY -> Priority.PRIORITY_HIGH_ACCURACY
    LocationAccuracyMode.BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
    LocationAccuracyMode.LOW_POWER -> Priority.PRIORITY_LOW_POWER
}

private fun Location.toSample(): LocationSample = LocationSample(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracy,
    altitudeMeters = if (hasAltitude()) altitude else null,
    speedMps = if (hasSpeed()) speed else null,
    bearingDeg = if (hasBearing()) bearing else null,
    sourceTimestampNs = elapsedRealtimeNanos,
    receiptUtcMillis = time,
)
