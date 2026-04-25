package com.lap.mobile.capture

/**
 * Channel-name dictionary for [MotionSample] → `TelemetrySample`
 * mapping (T069). Wire-stable strings — server-side ingestion
 * (ADR-012) decodes IMU telemetry by these exact keys; renaming
 * either side requires a coordinated change.
 *
 * Units are encoded in the names (`_mps2` for m/s², `_rps` for rad/s)
 * so a future "is this g or m/s²?" / "deg/s or rad/s?" question
 * is a single look at the channel string. Mirrors the
 * [LocationChannels] convention (`speed_mps`, `accuracy_m`).
 */
object MotionChannels {
    const val ACCEL_X_MPS2: String = "accel_x_mps2"
    const val ACCEL_Y_MPS2: String = "accel_y_mps2"
    const val ACCEL_Z_MPS2: String = "accel_z_mps2"
    const val GYRO_X_RPS: String = "gyro_x_rps"
    const val GYRO_Y_RPS: String = "gyro_y_rps"
    const val GYRO_Z_RPS: String = "gyro_z_rps"
}
