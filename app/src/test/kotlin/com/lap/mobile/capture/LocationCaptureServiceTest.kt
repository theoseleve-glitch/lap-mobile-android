package com.lap.mobile.capture

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val LATITUDE_A: Double = 47.643_222
private const val LATITUDE_B: Double = 47.643_300
private const val LONGITUDE: Double = 6.026_018
private const val ACCURACY_M: Float = 3.5f
private const val SOURCE_TS_NS_A: Long = 1_000L
private const val SOURCE_TS_NS_B: Long = 2_000L
private const val RECEIPT_MS_A: Long = 10L
private const val RECEIPT_MS_B: Long = 20L

/**
 * The capture service is a thin pass-through over the platform-
 * agnostic [LocationDataSource] — the real work (FLPC + LocationRequest
 * wiring) lives in [FusedLocationDataSource], which needs an Android
 * runtime to test (T083). These cases lock the JVM-seam contract:
 *
 *  - emissions reach the collector in order
 *  - the service doesn't consume or filter the underlying flow
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocationCaptureServiceTest {
    @Test
    fun forwards_samples_from_underlying_source_in_order() = runTest {
        val expected = listOf(
            sample(LATITUDE_A, SOURCE_TS_NS_A, RECEIPT_MS_A),
            sample(LATITUDE_B, SOURCE_TS_NS_B, RECEIPT_MS_B),
        )
        val service = LocationCaptureService(FixedLocationDataSource(expected))

        val collected = service.samples().toList()

        assertEquals(expected, collected)
    }

    @Test
    fun empty_source_yields_empty_flow() = runTest {
        val service = LocationCaptureService(FixedLocationDataSource(emptyList()))

        val collected = service.samples().toList()

        assertEquals(emptyList<LocationSample>(), collected)
    }

    private fun sample(latitude: Double, sourceTimestampNs: Long, receiptUtcMillis: Long): LocationSample =
        LocationSample(
            latitude = latitude,
            longitude = LONGITUDE,
            accuracyMeters = ACCURACY_M,
            altitudeMeters = null,
            speedMps = null,
            bearingDeg = null,
            sourceTimestampNs = sourceTimestampNs,
            receiptUtcMillis = receiptUtcMillis,
        )
}
