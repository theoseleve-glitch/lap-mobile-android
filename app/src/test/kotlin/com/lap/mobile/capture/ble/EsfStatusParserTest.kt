package com.lap.mobile.capture.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val FIXTURE_ITOW: Long = 400_000L
private const val FIXTURE_VERSION: Int = 2
private const val FIXTURE_FUSION_MODE: Int = 1
private const val SENSOR_A_STATUS1: Int = 0x12
private const val SENSOR_A_STATUS2: Int = 0x21
private const val SENSOR_A_FREQ: Int = 100
private const val SENSOR_A_FAULTS: Int = 0x00
private const val SENSOR_B_STATUS1: Int = 0x34
private const val SENSOR_B_STATUS2: Int = 0x43
private const val SENSOR_B_FREQ: Int = 50
private const val SENSOR_B_FAULTS: Int = 0x01
private const val EXPECTED_SENSOR_COUNT: Int = 2

class EsfStatusParserTest {
    @Test
    fun parses_two_sensor_status_records() {
        val payload = buildPayload(
            iTOW = FIXTURE_ITOW,
            version = FIXTURE_VERSION,
            fusionMode = FIXTURE_FUSION_MODE,
            sensors = listOf(
                EsfSensorStatus(SENSOR_A_STATUS1, SENSOR_A_STATUS2, SENSOR_A_FREQ, SENSOR_A_FAULTS),
                EsfSensorStatus(SENSOR_B_STATUS1, SENSOR_B_STATUS2, SENSOR_B_FREQ, SENSOR_B_FAULTS),
            ),
        )

        val parsed = requireNotNull(EsfStatusParser.parse(payload))

        assertEquals(FIXTURE_ITOW, parsed.iTOW)
        assertEquals(FIXTURE_VERSION, parsed.version)
        assertEquals(FIXTURE_FUSION_MODE, parsed.fusionMode)
        assertEquals(EXPECTED_SENSOR_COUNT, parsed.sensors.size)
        assertEquals(SENSOR_A_STATUS1, parsed.sensors[0].sensStatus1)
        assertEquals(SENSOR_A_FREQ, parsed.sensors[0].frequencyHz)
        assertEquals(SENSOR_B_STATUS1, parsed.sensors[1].sensStatus1)
        assertEquals(SENSOR_B_FAULTS, parsed.sensors[1].faults)
    }

    @Test
    fun parses_zero_sensors_when_only_header_present() {
        val payload = buildPayload(
            iTOW = FIXTURE_ITOW,
            version = FIXTURE_VERSION,
            fusionMode = FIXTURE_FUSION_MODE,
            sensors = emptyList(),
        )

        val parsed = requireNotNull(EsfStatusParser.parse(payload))

        assertEquals(0, parsed.sensors.size)
    }

    @Test
    fun returns_null_when_payload_too_short_for_header() {
        assertNull(EsfStatusParser.parse(ByteArray(EsfStatusParser.HEADER_SIZE - 1)))
    }

    @Test
    fun returns_null_when_payload_size_inconsistent_with_sensor_count() {
        // header(16) + 3 bytes (not multiple of 4)
        assertNull(EsfStatusParser.parse(ByteArray(EsfStatusParser.HEADER_SIZE + 3)))
    }

    private fun buildPayload(iTOW: Long, version: Int, fusionMode: Int, sensors: List<EsfSensorStatus>): ByteArray {
        val size = EsfStatusParser.HEADER_SIZE + sensors.size * EsfStatusParser.SENSOR_RECORD_SIZE
        val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(iTOW.toInt()) // 0  iTOW (U4)
        buffer.put(version.toByte()) // 4  version (U1)
        repeat(7) { buffer.put(0) } // 5-11 reserved1
        buffer.put(fusionMode.toByte()) // 12 fusionMode (U1)
        buffer.putShort(0) // 13-14 reserved2
        buffer.put(sensors.size.toByte()) // 15 numSens (U1)
        for (s in sensors) {
            buffer.put(s.sensStatus1.toByte())
            buffer.put(s.sensStatus2.toByte())
            buffer.put(s.frequencyHz.toByte())
            buffer.put(s.faults.toByte())
        }
        return buffer.array()
    }
}
