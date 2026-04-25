package com.lap.mobile.data.bundle

import com.lap.schemas.v1.SensorConfig
import com.lap.schemas.v1.SensorConfigSnapshot

/**
 * Live capture-config snapshot inputs (T139). Carries the per-sensor
 * configured rates plus the optional RaceBox identity that comes from
 * MON-VER (firmware) and SEC-UNIQID (hardware id) once the BLE link
 * is established. The phone's deviceModel / OS / OS version live on
 * the manifest itself (already populated upstream by T132); this DTO
 * is just the SensorConfigSnapshot inputs.
 *
 * `raceboxRateHz` defaults to 0.0 (matches a disabled sensor's rate);
 * `raceboxHardwareId` / `raceboxFirmware` default to null. When
 * [raceboxEnabled] is false, both stale identity bits are dropped at
 * build time so a phone-only session never falsely attributes
 * samples to a previously-paired RaceBox.
 */
data class CaptureSensorRates(
    val locationHz: Double,
    val motionHz: Double,
    val raceboxEnabled: Boolean,
    val raceboxRateHz: Double = 0.0,
    val raceboxHardwareId: String? = null,
    val raceboxFirmware: String? = null,
)

/**
 * Builds [SensorConfigSnapshot] for the bundle manifest (T139). The
 * builder is pure — given the [CaptureSensorRates] DTO, it produces
 * the snapshot in one pass. Sensor names ("gnss", "imu",
 * "racebox_ble") are wire-stable strings the server-side ingest
 * (ADR-012) keys on, hoisted to constants so a name-rename is a
 * single-site change (and a coordinated server change).
 *
 * Every sensor always appears in the list — even disabled ones —
 * because the server uses the absence/presence + enabled flag to
 * tell "session didn't have this sensor available" from "session had
 * it but it was off". Disabled sensors carry their rate as 0.0 so
 * the schema row stays uniform.
 */
object SensorConfigSnapshotBuilder {
    const val SENSOR_GNSS: String = "gnss"
    const val SENSOR_IMU: String = "imu"
    const val SENSOR_RACEBOX_BLE: String = "racebox_ble"

    fun build(rates: CaptureSensorRates): SensorConfigSnapshot = SensorConfigSnapshot(
        sensors = listOf(
            SensorConfig(name = SENSOR_GNSS, sampleRateHz = rates.locationHz, enabled = true),
            SensorConfig(name = SENSOR_IMU, sampleRateHz = rates.motionHz, enabled = true),
            SensorConfig(
                name = SENSOR_RACEBOX_BLE,
                sampleRateHz = if (rates.raceboxEnabled) rates.raceboxRateHz else 0.0,
                enabled = rates.raceboxEnabled,
            ),
        ),
        hardwareId = if (rates.raceboxEnabled) rates.raceboxHardwareId else null,
        firmwareVersion = if (rates.raceboxEnabled) rates.raceboxFirmware else null,
    )
}
