package com.lap.mobile.capture.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val ACCEL_X_TYPE: Int = 16
private const val GYRO_X_TYPE: Int = 14
private const val ACCEL_X_RAW: Int = 0x0001_2345
private const val GYRO_X_RAW: Int = -1_500
private const val ACCEL_X_TTAG: Long = 100_000L
private const val GYRO_X_TTAG: Long = 100_010L
private const val EXPECTED_TWO_MEASUREMENTS: Int = 2

class EsfRawParserTest {
    @Test
    fun parses_single_measurement() {
        val payload = buildPayload(
            listOf(
                EsfRawMeasurement(ACCEL_X_TYPE, ACCEL_X_RAW, ACCEL_X_TTAG),
            ),
        )

        val parsed = requireNotNull(EsfRawParser.parse(payload))

        assertEquals(1, parsed.measurements.size)
        assertEquals(ACCEL_X_TYPE, parsed.measurements[0].dataType)
        assertEquals(ACCEL_X_RAW, parsed.measurements[0].dataValue)
        assertEquals(ACCEL_X_TTAG, parsed.measurements[0].sensorTimeTag)
    }

    @Test
    fun parses_multiple_measurements() {
        val payload = buildPayload(
            listOf(
                EsfRawMeasurement(ACCEL_X_TYPE, ACCEL_X_RAW, ACCEL_X_TTAG),
                EsfRawMeasurement(GYRO_X_TYPE, GYRO_X_RAW, GYRO_X_TTAG),
            ),
        )

        val parsed = requireNotNull(EsfRawParser.parse(payload))

        assertEquals(EXPECTED_TWO_MEASUREMENTS, parsed.measurements.size)
        assertEquals(ACCEL_X_TYPE, parsed.measurements[0].dataType)
        assertEquals(GYRO_X_TYPE, parsed.measurements[1].dataType)
    }

    @Test
    fun parses_negative_data_value_via_24_bit_sign_extension() {
        val payload = buildPayload(
            listOf(EsfRawMeasurement(GYRO_X_TYPE, GYRO_X_RAW, GYRO_X_TTAG)),
        )

        val parsed = requireNotNull(EsfRawParser.parse(payload))

        assertEquals(
            "negative 24-bit raw must sign-extend to negative Int",
            GYRO_X_RAW,
            parsed.measurements[0].dataValue,
        )
    }

    @Test
    fun returns_null_when_payload_smaller_than_header() {
        assertNull(EsfRawParser.parse(ByteArray(EsfRawParser.HEADER_SIZE - 1)))
    }

    @Test
    fun returns_null_when_payload_size_not_aligned_to_measurement() {
        // header(4) + 7 bytes = not a multiple of MEASUREMENT_SIZE(8)
        assertNull(EsfRawParser.parse(ByteArray(EsfRawParser.HEADER_SIZE + 7)))
    }

    @Test
    fun parses_zero_measurements_when_only_header_present() {
        val payload = ByteArray(EsfRawParser.HEADER_SIZE)

        val parsed = requireNotNull(EsfRawParser.parse(payload))

        assertEquals(0, parsed.measurements.size)
    }

    private fun buildPayload(measurements: List<EsfRawMeasurement>): ByteArray {
        val size = EsfRawParser.HEADER_SIZE + measurements.size * EsfRawParser.MEASUREMENT_SIZE
        val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0) // 4-byte reserved header
        for (m in measurements) {
            // data field: low 24 bits = value (sign-truncated to 24-bit), high 8 bits = type
            val low24 = m.dataValue and 0x00FF_FFFF
            val packed = (m.dataType shl 24) or low24
            buffer.putInt(packed)
            buffer.putInt(m.sensorTimeTag.toInt())
        }
        return buffer.array()
    }
}
