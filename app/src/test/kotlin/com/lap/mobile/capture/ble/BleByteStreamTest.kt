package com.lap.mobile.capture.ble

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val FRAGMENT_A: ByteArray = byteArrayOf(0xB5.toByte(), 0x62.toByte(), 0x01, 0x07)
private val FRAGMENT_B: ByteArray = byteArrayOf(0x5C, 0x00, 0xCC.toByte(), 0xDD.toByte())
private val FRAGMENT_C: ByteArray = byteArrayOf(0x00, 0x00, 0x00, 0x00)
private const val EXPECTED_FRAGMENT_COUNT: Int = 3

/**
 * Unit tests for [BleByteStream] (T105). The stream is a thin
 * pass-through over [BleNotificationSource] — verifies the TX
 * characteristic UUID is what gets subscribed to, and raw
 * notification payloads flow through unmodified (T106's framer
 * does the UBX reassembly downstream).
 */
class BleByteStreamTest {
    @Test
    fun bytes_subscribes_to_tx_characteristic_uuid() = runTest {
        var capturedUuid: String? = null
        val source = BleNotificationSource { uuid ->
            capturedUuid = uuid
            flowOf()
        }
        val chars = UartCharacteristics(
            serviceUuid = UartUuids.SERVICE,
            txUuid = UartUuids.TX,
            rxUuid = UartUuids.RX,
        )
        val stream = BleByteStream(source = source, characteristics = chars)

        stream.bytes().toList()

        assertEquals(UartUuids.TX, capturedUuid)
    }

    @Test
    fun bytes_propagates_raw_notification_payloads_unmodified() = runTest {
        val payloads = listOf(FRAGMENT_A, FRAGMENT_B, FRAGMENT_C)
        val source = BleNotificationSource { _ -> flowOf(*payloads.toTypedArray()) }
        val chars = UartCharacteristics(
            serviceUuid = UartUuids.SERVICE,
            txUuid = UartUuids.TX,
            rxUuid = UartUuids.RX,
        )
        val stream = BleByteStream(source = source, characteristics = chars)

        val collected = stream.bytes().toList()

        assertEquals(EXPECTED_FRAGMENT_COUNT, collected.size)
        assertArrayContentsEqual("fragment 0", payloads[0], collected[0])
        assertArrayContentsEqual("fragment 1", payloads[1], collected[1])
        assertArrayContentsEqual("fragment 2", payloads[2], collected[2])
    }

    @Test
    fun bytes_with_no_notifications_returns_empty() = runTest {
        val source = BleNotificationSource { _ -> flowOf() }
        val chars = UartCharacteristics(
            serviceUuid = UartUuids.SERVICE,
            txUuid = UartUuids.TX,
            rxUuid = UartUuids.RX,
        )
        val stream = BleByteStream(source = source, characteristics = chars)

        assertTrue(stream.bytes().toList().isEmpty())
    }

    private fun assertArrayContentsEqual(label: String, expected: ByteArray, actual: ByteArray) {
        assertTrue("$label content mismatch", expected.contentEquals(actual))
    }
}
