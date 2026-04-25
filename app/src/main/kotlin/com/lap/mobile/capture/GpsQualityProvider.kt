package com.lap.mobile.capture

/**
 * Latest GNSS quality reading for [GpsFixQualityCheck] (T091).
 * fun-interface seam over `LocationManager` + `GnssStatus` so the
 * preflight policy is JVM-testable; the Android impl reads from the
 * already-running [LocationCaptureService] (T061) plus a
 * `GnssStatus.Callback`.
 */
fun interface GpsQualityProvider {
    suspend fun snapshot(): GpsQualitySnapshot
}
