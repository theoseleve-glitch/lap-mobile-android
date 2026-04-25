package com.lap.mobile.capture

private const val MICROS_PER_SAMPLE_AT_50HZ: Int = 20_000

/**
 * Sampling-rate spec passed to [SensorManagerMotionDataSource] when
 * registering accelerometer + gyroscope listeners. T068's bar is
 * "SensorManager … at 50 Hz", encoded here as [FIFTY_HERTZ] so the
 * DI module wires the same config the orchestrator (T071) and
 * battery-aware throttling (T073) reference by name.
 *
 * `samplingPeriodMicros` matches the parameter Android's
 * `SensorManager.registerListener(..., samplingPeriodUs)` accepts —
 * the unit is in the field name to defuse millis/micros confusion at
 * the Hilt-binding seam.
 */
data class MotionCaptureConfig(
    val samplingPeriodMicros: Int,
) {
    companion object {
        val FIFTY_HERTZ: MotionCaptureConfig = MotionCaptureConfig(
            samplingPeriodMicros = MICROS_PER_SAMPLE_AT_50HZ,
        )
    }
}
