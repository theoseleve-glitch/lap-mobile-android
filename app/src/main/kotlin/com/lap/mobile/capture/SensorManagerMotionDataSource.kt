package com.lap.mobile.capture

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val ACCEL_AXIS_X: Int = 0
private const val ACCEL_AXIS_Y: Int = 1
private const val ACCEL_AXIS_Z: Int = 2

/**
 * Production [MotionDataSource]. Wraps [SensorManager] for
 * accelerometer + gyroscope at the rate from [config], routing each
 * `SensorEvent` into the matching [MotionSample] subtype.
 *
 * If a device is missing one of the two sensors, that stream simply
 * never emits — the orchestrator (T071) tolerates partial IMU
 * availability rather than failing the whole session.
 *
 * Live verification needs an Android device with real sensors; that's
 * T083's instrumented capture test — JVM tests cover only the
 * platform-agnostic [MotionCaptureService] over [MotionDataSource].
 */
class SensorManagerMotionDataSource(
    private val sensorManager: SensorManager,
    private val config: MotionCaptureConfig = MotionCaptureConfig.FIFTY_HERTZ,
) : MotionDataSource {
    override fun samples(): Flow<MotionSample> = callbackFlow {
        val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> trySend(
                        MotionSample.Accelerometer(
                            xMps2 = event.values[ACCEL_AXIS_X],
                            yMps2 = event.values[ACCEL_AXIS_Y],
                            zMps2 = event.values[ACCEL_AXIS_Z],
                            sourceTimestampNs = event.timestamp,
                        ),
                    )
                    Sensor.TYPE_GYROSCOPE -> trySend(
                        MotionSample.Gyroscope(
                            xRadPerSec = event.values[ACCEL_AXIS_X],
                            yRadPerSec = event.values[ACCEL_AXIS_Y],
                            zRadPerSec = event.values[ACCEL_AXIS_Z],
                            sourceTimestampNs = event.timestamp,
                        ),
                    )
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        accelerometer?.let { sensorManager.registerListener(listener, it, config.samplingPeriodMicros) }
        gyroscope?.let { sensorManager.registerListener(listener, it, config.samplingPeriodMicros) }

        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
