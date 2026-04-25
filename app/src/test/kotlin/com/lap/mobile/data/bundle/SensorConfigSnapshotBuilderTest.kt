package com.lap.mobile.data.bundle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val LOCATION_HZ: Double = 10.0
private const val MOTION_HZ: Double = 100.0
private const val RACEBOX_HZ: Double = 25.0
private const val RACEBOX_FIRMWARE: String = "EXT CORE 1.00 (a1b2c3)"
private const val RACEBOX_HARDWARE_ID: String = "00aabbccdd"

class SensorConfigSnapshotBuilderTest {
    @Test
    fun gnss_sensor_carries_phone_location_rate() {
        val snapshot = SensorConfigSnapshotBuilder.build(
            CaptureSensorRates(
                locationHz = LOCATION_HZ,
                motionHz = MOTION_HZ,
                raceboxEnabled = false,
            ),
        )

        val gnss = snapshot.sensors.first { it.name == SensorConfigSnapshotBuilder.SENSOR_GNSS }
        assertEquals(LOCATION_HZ, gnss.sampleRateHz, 0.0)
        assertTrue("GNSS sensor must be enabled when locationHz > 0", gnss.enabled)
    }

    @Test
    fun imu_sensor_carries_phone_motion_rate() {
        val snapshot = SensorConfigSnapshotBuilder.build(
            CaptureSensorRates(
                locationHz = LOCATION_HZ,
                motionHz = MOTION_HZ,
                raceboxEnabled = false,
            ),
        )

        val imu = snapshot.sensors.first { it.name == SensorConfigSnapshotBuilder.SENSOR_IMU }
        assertEquals(MOTION_HZ, imu.sampleRateHz, 0.0)
        assertTrue("IMU sensor must be enabled when motionHz > 0", imu.enabled)
    }

    @Test
    fun racebox_ble_sensor_enabled_only_when_racebox_connected() {
        val withRacebox = SensorConfigSnapshotBuilder.build(
            CaptureSensorRates(
                locationHz = LOCATION_HZ,
                motionHz = MOTION_HZ,
                raceboxEnabled = true,
                raceboxRateHz = RACEBOX_HZ,
            ),
        )
        val withoutRacebox = SensorConfigSnapshotBuilder.build(
            CaptureSensorRates(
                locationHz = LOCATION_HZ,
                motionHz = MOTION_HZ,
                raceboxEnabled = false,
            ),
        )

        val ble = withRacebox.sensors.first { it.name == SensorConfigSnapshotBuilder.SENSOR_RACEBOX_BLE }
        assertEquals(RACEBOX_HZ, ble.sampleRateHz, 0.0)
        assertTrue(ble.enabled)
        val bleOff = withoutRacebox.sensors.first { it.name == SensorConfigSnapshotBuilder.SENSOR_RACEBOX_BLE }
        assertEquals(0.0, bleOff.sampleRateHz, 0.0)
        assertEquals(false, bleOff.enabled)
    }

    @Test
    fun hardware_id_propagates_to_snapshot_only_when_racebox_connected() {
        val withRacebox = SensorConfigSnapshotBuilder.build(
            CaptureSensorRates(
                locationHz = LOCATION_HZ,
                motionHz = MOTION_HZ,
                raceboxEnabled = true,
                raceboxRateHz = RACEBOX_HZ,
                raceboxHardwareId = RACEBOX_HARDWARE_ID,
            ),
        )

        assertEquals(RACEBOX_HARDWARE_ID, withRacebox.hardwareId)
    }

    @Test
    fun firmware_version_propagates_to_snapshot_only_when_racebox_connected() {
        val withRacebox = SensorConfigSnapshotBuilder.build(
            CaptureSensorRates(
                locationHz = LOCATION_HZ,
                motionHz = MOTION_HZ,
                raceboxEnabled = true,
                raceboxRateHz = RACEBOX_HZ,
                raceboxFirmware = RACEBOX_FIRMWARE,
            ),
        )

        assertEquals(RACEBOX_FIRMWARE, withRacebox.firmwareVersion)
    }

    @Test
    fun racebox_metadata_absent_when_disconnected_so_server_does_not_assume_stale_id() {
        // Stale hardware/firmware on a phone-only session would falsely
        // attribute samples to a RaceBox the user isn't actually using.
        val phoneOnly = SensorConfigSnapshotBuilder.build(
            CaptureSensorRates(
                locationHz = LOCATION_HZ,
                motionHz = MOTION_HZ,
                raceboxEnabled = false,
                raceboxHardwareId = "stale-from-prior-session",
                raceboxFirmware = "stale-firmware",
            ),
        )

        assertNull(phoneOnly.hardwareId)
        assertNull(phoneOnly.firmwareVersion)
    }
}
