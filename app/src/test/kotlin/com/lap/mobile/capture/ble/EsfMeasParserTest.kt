package com.lap.mobile.capture.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val FIXTURE_TIME_TAG: Long = 250_000L
private const val FIXTURE_ID: Int = 17
private const val ACCEL_X_TYPE: Int = 16
private const val GYRO_X_TYPE: Int = 14
private const val ACCEL_X_RAW: Int = 0x0001_2345
private const val GYRO_X_RAW: Int = -2_000
private const val FIXTURE_CALIB_TTAG: Long = 250_500L
private const val ZERO_MEAS_FLAGS: Int = 0x0000
private const val TWO_MEAS_FLAGS: Int = 0x0002

// flags bit 3 (calibTtagValid) signals the trailing calibTtag word is present
private const val ONE_MEAS_WITH_CALIB_FLAGS: Int = 0x0009
private const val EXPECTED_TWO_MEAS: Int = 2

class EsfMeasParserTest {
    @Test
    fun parses_two_measurements_without_calib_ttag() {
        val payload = buildPayload(
            timeTag = FIXTURE_TIME_TAG,
            flags = TWO_MEAS_FLAGS,
            id = FIXTURE_ID,
            measurements = listOf(
                EsfMeasMeasurement(ACCEL_X_TYPE, ACCEL_X_RAW),
                EsfMeasMeasurement(GYRO_X_TYPE, GYRO_X_RAW),
            ),
            calibTtag = null,
        )

        val parsed = requireNotNull(EsfMeasParser.parse(payload))

        assertEquals(FIXTURE_TIME_TAG, parsed.timeTag)
        assertEquals(TWO_MEAS_FLAGS, parsed.flags)
        assertEquals(FIXTURE_ID, parsed.id)
        assertEquals(EXPECTED_TWO_MEAS, parsed.measurements.size)
        assertEquals(ACCEL_X_TYPE, parsed.measurements[0].dataType)
        assertEquals(ACCEL_X_RAW, parsed.measurements[0].dataValue)
        assertEquals(GYRO_X_TYPE, parsed.measurements[1].dataType)
        assertEquals(GYRO_X_RAW, parsed.measurements[1].dataValue)
        assertNull(parsed.calibTimeTag)
    }

    @Test
    fun parses_with_calib_ttag_when_flags_bit_3_set() {
        val payload = buildPayload(
            timeTag = FIXTURE_TIME_TAG,
            flags = ONE_MEAS_WITH_CALIB_FLAGS,
            id = FIXTURE_ID,
            measurements = listOf(EsfMeasMeasurement(ACCEL_X_TYPE, ACCEL_X_RAW)),
            calibTtag = FIXTURE_CALIB_TTAG,
        )

        val parsed = requireNotNull(EsfMeasParser.parse(payload))

        assertEquals(1, parsed.measurements.size)
        assertNotNull(parsed.calibTimeTag)
        assertEquals(FIXTURE_CALIB_TTAG, parsed.calibTimeTag)
    }

    @Test
    fun parses_zero_measurements_when_only_header_present() {
        val payload = buildPayload(
            timeTag = FIXTURE_TIME_TAG,
            flags = ZERO_MEAS_FLAGS,
            id = FIXTURE_ID,
            measurements = emptyList(),
            calibTtag = null,
        )

        val parsed = requireNotNull(EsfMeasParser.parse(payload))

        assertEquals(0, parsed.measurements.size)
    }

    @Test
    fun returns_null_when_payload_too_short_for_header() {
        assertNull(EsfMeasParser.parse(ByteArray(EsfMeasParser.HEADER_SIZE - 1)))
    }

    @Test
    fun returns_null_when_payload_size_inconsistent_with_measurement_count() {
        // header + 1 byte (not multiple of 4)
        assertNull(EsfMeasParser.parse(ByteArray(EsfMeasParser.HEADER_SIZE + 1)))
    }

    private fun buildPayload(
        timeTag: Long,
        flags: Int,
        id: Int,
        measurements: List<EsfMeasMeasurement>,
        calibTtag: Long?,
    ): ByteArray {
        val measurementBytes = measurements.size * EsfMeasParser.MEASUREMENT_SIZE
        val calibBytes = if (calibTtag != null) EsfMeasParser.CALIB_TTAG_SIZE else 0
        val size = EsfMeasParser.HEADER_SIZE + measurementBytes + calibBytes
        val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(timeTag.toInt())
        buffer.putShort(flags.toShort())
        buffer.putShort(id.toShort())
        for (m in measurements) {
            val low24 = m.dataValue and 0x00FF_FFFF
            val packed = (m.dataType shl 24) or low24
            buffer.putInt(packed)
        }
        if (calibTtag != null) buffer.putInt(calibTtag.toInt())
        return buffer.array()
    }
}
