package com.lap.mobile.capture.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val DEG_SCALE: Double = 1e-7
private const val ATTITUDE_SCALE: Double = 1e-5
private const val U1_TO_INT_MASK: Int = 0xFF
private const val U4_TO_LONG_MASK: Long = 0xFFFF_FFFFL

private const val OFFSET_ITOW: Int = 0
private const val OFFSET_FIX_TYPE: Int = 20
private const val OFFSET_NUM_SV: Int = 23
private const val OFFSET_LON: Int = 24
private const val OFFSET_LAT: Int = 28
private const val OFFSET_HEIGHT: Int = 32
private const val OFFSET_VEL_N: Int = 48
private const val OFFSET_VEL_E: Int = 52
private const val OFFSET_VEL_D: Int = 56
private const val OFFSET_GSPEED: Int = 60
private const val OFFSET_HEAD_MOT: Int = 64
private const val OFFSET_VEH_ROLL: Int = 84
private const val OFFSET_VEH_PITCH: Int = 88
private const val OFFSET_VEH_HEADING: Int = 92

/**
 * Decodes UBX-NAV-PVAT (T109). 116-byte payload — class/message
 * 0x01/0x17. Same NAV-PVT prefix layout (offsets ≤ 80) plus the
 * vehicle-attitude tail (roll / pitch / heading at 84/88/92).
 *
 * Attitude is pre-scaled to degrees (raw is degrees × 1e-5),
 * matching [NavPvt.headingDeg] convention.
 */
object NavPvatParser {
    const val PAYLOAD_SIZE: Int = 116
    const val CLASS_ID: Int = 0x01
    const val MESSAGE_ID: Int = 0x17

    fun parse(payload: ByteArray): NavPvat? {
        if (payload.size != PAYLOAD_SIZE) return null
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        return NavPvat(
            iTOW = buffer.getInt(OFFSET_ITOW).toLong() and U4_TO_LONG_MASK,
            fixType = buffer.get(OFFSET_FIX_TYPE).toInt() and U1_TO_INT_MASK,
            numSatellites = buffer.get(OFFSET_NUM_SV).toInt() and U1_TO_INT_MASK,
            longitudeDeg = buffer.getInt(OFFSET_LON) * DEG_SCALE,
            latitudeDeg = buffer.getInt(OFFSET_LAT) * DEG_SCALE,
            heightMm = buffer.getInt(OFFSET_HEIGHT),
            velocityNorthMmPerSec = buffer.getInt(OFFSET_VEL_N),
            velocityEastMmPerSec = buffer.getInt(OFFSET_VEL_E),
            velocityDownMmPerSec = buffer.getInt(OFFSET_VEL_D),
            groundSpeedMmPerSec = buffer.getInt(OFFSET_GSPEED),
            vehicleRollDeg = buffer.getInt(OFFSET_VEH_ROLL) * ATTITUDE_SCALE,
            vehiclePitchDeg = buffer.getInt(OFFSET_VEH_PITCH) * ATTITUDE_SCALE,
            vehicleHeadingDeg = buffer.getInt(OFFSET_VEH_HEADING) * ATTITUDE_SCALE,
            motionHeadingDeg = buffer.getInt(OFFSET_HEAD_MOT) * ATTITUDE_SCALE,
        )
    }
}
