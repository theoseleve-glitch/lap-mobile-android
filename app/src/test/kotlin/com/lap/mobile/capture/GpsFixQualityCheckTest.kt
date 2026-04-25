package com.lap.mobile.capture

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val GOOD_ACCURACY: Float = 5.0f
private const val EXACT_ACCURACY_LIMIT: Float = 20.0f
private const val POOR_ACCURACY: Float = 25.0f
private const val GOOD_SAT_COUNT: Int = 8
private const val EXACT_MIN_SATS: Int = 6
private const val LOW_SAT_COUNT: Int = 4

/**
 * Unit tests for [GpsFixQualityCheck] (T091). Hard preflight block
 * when accuracy > 20 m or satellites < 6 — the "wait" override the
 * task spec mentions is a UI affordance (T097) layered above this
 * check, not check-side logic.
 *
 * Failure priority: no-fix → accuracy → satellites. Surfacing only
 * one reason at a time keeps the readiness UI uncluttered while the
 * receiver is still warming up.
 */
class GpsFixQualityCheckTest {
    @Test
    fun no_fix_returns_no_fix_reason() = runTest {
        val provider = GpsQualityProvider { GpsQualitySnapshot(accuracyMeters = null, satelliteCount = 0) }
        val check = GpsFixQualityCheck(provider)

        assertEquals(GpsFixQualityCheck.REASON_GPS_NO_FIX, check.failureReason())
    }

    @Test
    fun poor_accuracy_returns_accuracy_reason() = runTest {
        val provider = GpsQualityProvider { GpsQualitySnapshot(POOR_ACCURACY, GOOD_SAT_COUNT) }
        val check = GpsFixQualityCheck(provider)

        assertEquals(GpsFixQualityCheck.REASON_GPS_ACCURACY, check.failureReason())
    }

    @Test
    fun low_satellites_returns_satellites_reason() = runTest {
        val provider = GpsQualityProvider { GpsQualitySnapshot(GOOD_ACCURACY, LOW_SAT_COUNT) }
        val check = GpsFixQualityCheck(provider)

        assertEquals(GpsFixQualityCheck.REASON_GPS_SATELLITES, check.failureReason())
    }

    @Test
    fun good_accuracy_and_sufficient_satellites_returns_null() = runTest {
        val provider = GpsQualityProvider { GpsQualitySnapshot(GOOD_ACCURACY, GOOD_SAT_COUNT) }
        val check = GpsFixQualityCheck(provider)

        assertNull(check.failureReason())
    }

    @Test
    fun accuracy_exactly_at_limit_passes() = runTest {
        val provider = GpsQualityProvider {
            GpsQualitySnapshot(EXACT_ACCURACY_LIMIT, GOOD_SAT_COUNT)
        }
        val check = GpsFixQualityCheck(provider)

        assertNull("≤ 20 m is the boundary — exactly 20 m must pass", check.failureReason())
    }

    @Test
    fun satellites_exactly_at_minimum_passes() = runTest {
        val provider = GpsQualityProvider { GpsQualitySnapshot(GOOD_ACCURACY, EXACT_MIN_SATS) }
        val check = GpsFixQualityCheck(provider)

        assertNull("≥ 6 sats is the boundary — exactly 6 must pass", check.failureReason())
    }

    @Test
    fun no_fix_takes_priority_over_satellite_count() = runTest {
        val provider = GpsQualityProvider { GpsQualitySnapshot(accuracyMeters = null, satelliteCount = LOW_SAT_COUNT) }
        val check = GpsFixQualityCheck(provider)

        assertEquals(
            "while still searching, no-fix is the actionable reason",
            GpsFixQualityCheck.REASON_GPS_NO_FIX,
            check.failureReason(),
        )
    }

    @Test
    fun accuracy_takes_priority_over_satellite_count() = runTest {
        val provider = GpsQualityProvider { GpsQualitySnapshot(POOR_ACCURACY, LOW_SAT_COUNT) }
        val check = GpsFixQualityCheck(provider)

        assertEquals(GpsFixQualityCheck.REASON_GPS_ACCURACY, check.failureReason())
    }
}
