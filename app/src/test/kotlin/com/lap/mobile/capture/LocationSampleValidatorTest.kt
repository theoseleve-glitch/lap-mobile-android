package com.lap.mobile.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val LAT_VALID: Double = 47.643_222
private const val LAT_NORTH_POLE: Double = 90.0
private const val LAT_SOUTH_POLE: Double = -90.0
private const val LAT_OVER_NORTH: Double = 90.000_1
private const val LAT_UNDER_SOUTH: Double = -90.000_1

private const val LON_VALID: Double = 6.026_018
private const val LON_ANTIMERIDIAN_POS: Double = 180.0
private const val LON_ANTIMERIDIAN_NEG: Double = -180.0
private const val LON_OVER_EAST: Double = 180.000_1
private const val LON_UNDER_WEST: Double = -180.000_1

private const val ACCURACY_VALID: Float = 3.5f
private const val ACCURACY_ZERO: Float = 0.0f
private const val ACCURACY_NEGATIVE: Float = -1.0f

private const val SPEED_VALID: Float = 38.7f
private const val SPEED_ZERO: Float = 0.0f
private const val SPEED_NEGATIVE: Float = -0.001f

private const val BEARING_NORTH: Float = 0.0f
private const val BEARING_VALID: Float = 187.2f
private const val BEARING_JUST_UNDER_360: Float = 359.999f
private const val BEARING_360: Float = 360.0f
private const val BEARING_NEGATIVE: Float = -0.001f

private const val ALTITUDE_VALID: Double = 612.4
private const val SOURCE_TS_NS: Long = 1_234_567_890L
private const val RECEIPT_MS: Long = 1_745_487_600_123L

/**
 * Physical-bounds validator (T066). The job here is to flag samples
 * with values that can't be true on Earth — a bearing of 400° or a
 * latitude of 91° is a sensor or driver bug, not a borderline GPS
 * fix. The accuracy-threshold-for-go check (`accuracy ≤ 20 m`) is
 * T091's preflight gate and intentionally not bundled in.
 */
class LocationSampleValidatorTest {
    private val validator = LocationSampleValidator()

    @Test
    fun fully_populated_in_range_sample_has_no_violations() {
        assertTrue(validator.violations(validSample()).isEmpty())
    }

    @Test
    fun isValid_returns_true_when_violations_empty() {
        assertTrue(validator.isValid(validSample()))
    }

    @Test
    fun null_optionals_do_not_trigger_violations() {
        val sample = validSample().copy(speedMps = null, bearingDeg = null, altitudeMeters = null)
        assertEquals(emptySet<LocationFieldViolation>(), validator.violations(sample))
    }

    @Test
    fun north_pole_is_valid_latitude() {
        assertTrue(validator.violations(validSample().copy(latitude = LAT_NORTH_POLE)).isEmpty())
    }

    @Test
    fun south_pole_is_valid_latitude() {
        assertTrue(validator.violations(validSample().copy(latitude = LAT_SOUTH_POLE)).isEmpty())
    }

    @Test
    fun latitude_over_north_pole_is_out_of_range() {
        assertEquals(
            setOf(LocationFieldViolation.LATITUDE_OUT_OF_RANGE),
            validator.violations(validSample().copy(latitude = LAT_OVER_NORTH)),
        )
    }

    @Test
    fun latitude_under_south_pole_is_out_of_range() {
        assertEquals(
            setOf(LocationFieldViolation.LATITUDE_OUT_OF_RANGE),
            validator.violations(validSample().copy(latitude = LAT_UNDER_SOUTH)),
        )
    }

    @Test
    fun antimeridian_longitudes_are_valid() {
        assertTrue(validator.violations(validSample().copy(longitude = LON_ANTIMERIDIAN_POS)).isEmpty())
        assertTrue(validator.violations(validSample().copy(longitude = LON_ANTIMERIDIAN_NEG)).isEmpty())
    }

    @Test
    fun longitude_past_either_antimeridian_is_out_of_range() {
        assertEquals(
            setOf(LocationFieldViolation.LONGITUDE_OUT_OF_RANGE),
            validator.violations(validSample().copy(longitude = LON_OVER_EAST)),
        )
        assertEquals(
            setOf(LocationFieldViolation.LONGITUDE_OUT_OF_RANGE),
            validator.violations(validSample().copy(longitude = LON_UNDER_WEST)),
        )
    }

    @Test
    fun zero_accuracy_is_treated_as_no_estimate_and_invalid() {
        // Android FLPC convention: accuracy = 0 means "no estimate available".
        // A real fix has a positive σ.
        assertEquals(
            setOf(LocationFieldViolation.ACCURACY_NOT_POSITIVE),
            validator.violations(validSample().copy(accuracyMeters = ACCURACY_ZERO)),
        )
    }

    @Test
    fun negative_accuracy_is_invalid() {
        assertEquals(
            setOf(LocationFieldViolation.ACCURACY_NOT_POSITIVE),
            validator.violations(validSample().copy(accuracyMeters = ACCURACY_NEGATIVE)),
        )
    }

    @Test
    fun zero_speed_is_valid_when_present() {
        // Driver standing still at the start line.
        assertTrue(validator.violations(validSample().copy(speedMps = SPEED_ZERO)).isEmpty())
    }

    @Test
    fun negative_speed_is_invalid() {
        assertEquals(
            setOf(LocationFieldViolation.SPEED_NEGATIVE),
            validator.violations(validSample().copy(speedMps = SPEED_NEGATIVE)),
        )
    }

    @Test
    fun bearing_zero_is_valid() {
        assertTrue(validator.violations(validSample().copy(bearingDeg = BEARING_NORTH)).isEmpty())
    }

    @Test
    fun bearing_just_under_360_is_valid() {
        assertTrue(validator.violations(validSample().copy(bearingDeg = BEARING_JUST_UNDER_360)).isEmpty())
    }

    @Test
    fun bearing_exactly_360_is_invalid() {
        // Half-open [0, 360) — 360° wraps to 0°. Allowing both creates duplicate
        // canonical values.
        assertEquals(
            setOf(LocationFieldViolation.BEARING_OUT_OF_RANGE),
            validator.violations(validSample().copy(bearingDeg = BEARING_360)),
        )
    }

    @Test
    fun bearing_negative_is_invalid() {
        assertEquals(
            setOf(LocationFieldViolation.BEARING_OUT_OF_RANGE),
            validator.violations(validSample().copy(bearingDeg = BEARING_NEGATIVE)),
        )
    }

    @Test
    fun multiple_violations_are_all_reported() {
        val mangled = validSample().copy(
            latitude = LAT_OVER_NORTH,
            accuracyMeters = ACCURACY_NEGATIVE,
            bearingDeg = BEARING_360,
        )
        assertEquals(
            setOf(
                LocationFieldViolation.LATITUDE_OUT_OF_RANGE,
                LocationFieldViolation.ACCURACY_NOT_POSITIVE,
                LocationFieldViolation.BEARING_OUT_OF_RANGE,
            ),
            validator.violations(mangled),
        )
    }

    private fun validSample(): LocationSample = LocationSample(
        latitude = LAT_VALID,
        longitude = LON_VALID,
        accuracyMeters = ACCURACY_VALID,
        altitudeMeters = ALTITUDE_VALID,
        speedMps = SPEED_VALID,
        bearingDeg = BEARING_VALID,
        sourceTimestampNs = SOURCE_TS_NS,
        receiptUtcMillis = RECEIPT_MS,
    )
}
