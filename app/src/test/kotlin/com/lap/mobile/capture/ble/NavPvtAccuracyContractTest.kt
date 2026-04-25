package com.lap.mobile.capture.ble

import com.lap.mobile.capture.LocationChannels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val OFFSET_HACC: Int = 40
private const val OFFSET_YEAR: Int = 4
private const val OFFSET_MONTH: Int = 6
private const val OFFSET_DAY: Int = 7
private const val OFFSET_HOUR: Int = 8
private const val OFFSET_MIN: Int = 9
private const val OFFSET_SEC: Int = 10
private const val FIXTURE_YEAR: Int = 2026
private const val FIXTURE_MONTH: Int = 4
private const val FIXTURE_DAY: Int = 25
private const val FIXTURE_HOUR: Int = 14
private const val FIXTURE_MIN: Int = 30
private const val FIXTURE_SEC: Int = 12
private const val TYPICAL_HACC_MM: Int = 2_500
private const val TYPICAL_ACCURACY_M: Double = 2.5
private const val MM_PER_METRE: Double = 1_000.0
private const val U4_MAX: Long = 0xFFFF_FFFFL

/**
 * T118 contract: horizontal accuracy populated from NAV-PVT hAcc
 * is always non-null on the resulting [com.lap.schemas.v1.TelemetrySample].
 * This is an end-to-end test (raw 92-byte UBX payload → [NavPvtParser]
 * → [toTelemetrySample]) — single-component tests in
 * RaceboxSampleMapperTest exercise the mapping in isolation; this one
 * locks in the integration so refactoring either side can't drop the
 * field silently.
 */
class NavPvtAccuracyContractTest {
    @Test
    fun accuracy_m_present_for_typical_hacc() {
        val sample = parseAndMap(hAccMm = TYPICAL_HACC_MM)

        val accuracy = sample.channels[LocationChannels.ACCURACY_M]
        assertNotNull("accuracy_m must be present in channel map", accuracy)
        assertEquals(TYPICAL_ACCURACY_M, accuracy!!, 0.0)
    }

    @Test
    fun accuracy_m_present_when_hacc_is_zero() {
        // u-blox spec permits hAcc=0 as a valid (perfect-estimate) value.
        // Server distinguishes "absent" from "0" — must not omit the channel.
        val sample = parseAndMap(hAccMm = 0)

        assertTrue(LocationChannels.ACCURACY_M in sample.channels)
        assertEquals(0.0, sample.channels.getValue(LocationChannels.ACCURACY_M), 0.0)
    }

    @Test
    fun accuracy_m_present_for_maximum_unsigned_hacc() {
        // U4 max — guards against negative-int signed arithmetic regressions
        // when reading the unsigned hAcc field.
        val sample = parseAndMap(hAccMm = U4_MAX.toInt())

        assertTrue(LocationChannels.ACCURACY_M in sample.channels)
        assertEquals(U4_MAX / MM_PER_METRE, sample.channels.getValue(LocationChannels.ACCURACY_M), 0.0)
    }

    private fun parseAndMap(hAccMm: Int): com.lap.schemas.v1.TelemetrySample {
        val payload = buildNavPvtPayloadWithHacc(hAccMm)
        val parsed = requireNotNull(NavPvtParser.parse(payload)) { "parser must accept fixture payload" }
        return parsed.toTelemetrySample()
    }

    private fun buildNavPvtPayloadWithHacc(hAccMm: Int): ByteArray {
        val payload = ByteArray(NavPvtParser.PAYLOAD_SIZE)
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(OFFSET_YEAR, FIXTURE_YEAR.toShort())
        payload[OFFSET_MONTH] = FIXTURE_MONTH.toByte()
        payload[OFFSET_DAY] = FIXTURE_DAY.toByte()
        payload[OFFSET_HOUR] = FIXTURE_HOUR.toByte()
        payload[OFFSET_MIN] = FIXTURE_MIN.toByte()
        payload[OFFSET_SEC] = FIXTURE_SEC.toByte()
        buffer.putInt(OFFSET_HACC, hAccMm)
        return payload
    }
}
