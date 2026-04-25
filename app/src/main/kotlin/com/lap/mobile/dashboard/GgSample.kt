package com.lap.mobile.dashboard

/**
 * One sample on the G-G diagram (T197).
 *
 * Two scalars in g-units (1 g = 9.80665 m/s²):
 *   - [latAccelG]: lateral acceleration. + = right-cornering force,
 *     - = left-cornering. Sign convention is "force the body of
 *     the car experiences" — a right turn pushes the driver/IMU
 *     leftward, which is positive lateral g in the SAE J670 chassis
 *     coordinate system (X-right, Y-forward, Z-down).
 *   - [lonAccelG]: longitudinal acceleration. + = forward
 *     acceleration (throttle), - = braking deceleration. Same SAE
 *     J670 convention.
 *
 * Decoupled from sensor-side IMU types so the projection kernel +
 * Compose chart can be JVM-tested + previewed without dragging the
 * raw accel pipeline into the gate. A future converter mapping the
 * raw IMU sample (m/s² in device coordinates) → GgSample (g-units
 * in chassis coordinates) belongs in capture/, not here.
 */
data class GgSample(
    val latAccelG: Double,
    val lonAccelG: Double,
)
