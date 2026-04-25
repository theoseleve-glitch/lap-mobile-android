package com.lap.mobile.capture.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val DEG_SCALE: Double = 1e-7
private const val HEAD_SCALE: Double = 1e-5
private const val U4_TO_LONG_MASK: Long = 0xFFFF_FFFFL
private const val U2_TO_INT_MASK: Int = 0xFFFF
private const val U1_TO_INT_MASK: Int = 0xFF

private const val OFFSET_ITOW: Int = 0
private const val OFFSET_YEAR: Int = 4
private const val OFFSET_MONTH: Int = 6
private const val OFFSET_DAY: Int = 7
private const val OFFSET_HOUR: Int = 8
private const val OFFSET_MIN: Int = 9
private const val OFFSET_SEC: Int = 10
private const val OFFSET_FIX_TYPE: Int = 20
private const val OFFSET_NUM_SV: Int = 23
private const val OFFSET_LON: Int = 24
private const val OFFSET_LAT: Int = 28
private const val OFFSET_HEIGHT: Int = 32
private const val OFFSET_HMSL: Int = 36
private const val OFFSET_HACC: Int = 40
private const val OFFSET_VACC: Int = 44
private const val OFFSET_VEL_N: Int = 48
private const val OFFSET_VEL_E: Int = 52
private const val OFFSET_VEL_D: Int = 56
private const val OFFSET_GSPEED: Int = 60
private const val OFFSET_HEAD: Int = 64

/**
 * Decodes UBX-NAV-PVT (T107). 92-byte fixed-length payload from the
 * u-blox spec; rejects any other size as malformed (the framer
 * already validated the checksum, but length-versus-class drift
 * means we should still be defensive).
 *
 * Field layout follows the u-blox protocol manual; offsets are
 * hoisted to named constants so adding a parser variant (e.g.,
 * NAV-PVAT in T109 which adds attitude after the same prefix) can
 * reuse the layout intuition without re-decoding from raw numbers.
 *
 * Unit conversions for position fields are applied at decode time
 * (lat/lon × 1e-7 to degrees, heading × 1e-5 to degrees) — the
 * upstream consumer expects degrees + mm, not raw scaled integers.
 * Distances stay in mm and velocities in mm/s; the wire mapping in
 * T117 ([RaceboxSampleMapper]) converts to metres / m/s for
 * [com.lap.schemas.v1.TelemetrySample] channels.
 */
object NavPvtParser {
    const val PAYLOAD_SIZE: Int = 92
    const val CLASS_ID: Int = 0x01
    const val MESSAGE_ID: Int = 0x07

    fun parse(payload: ByteArray): NavPvt? {
        if (payload.size != PAYLOAD_SIZE) return null
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        return NavPvt(
            iTOW = buffer.getInt(OFFSET_ITOW).toLong() and U4_TO_LONG_MASK,
            year = buffer.getShort(OFFSET_YEAR).toInt() and U2_TO_INT_MASK,
            month = buffer.get(OFFSET_MONTH).toInt() and U1_TO_INT_MASK,
            day = buffer.get(OFFSET_DAY).toInt() and U1_TO_INT_MASK,
            hour = buffer.get(OFFSET_HOUR).toInt() and U1_TO_INT_MASK,
            minute = buffer.get(OFFSET_MIN).toInt() and U1_TO_INT_MASK,
            second = buffer.get(OFFSET_SEC).toInt() and U1_TO_INT_MASK,
            fixType = buffer.get(OFFSET_FIX_TYPE).toInt() and U1_TO_INT_MASK,
            numSatellites = buffer.get(OFFSET_NUM_SV).toInt() and U1_TO_INT_MASK,
            longitudeDeg = buffer.getInt(OFFSET_LON) * DEG_SCALE,
            latitudeDeg = buffer.getInt(OFFSET_LAT) * DEG_SCALE,
            heightMm = buffer.getInt(OFFSET_HEIGHT),
            heightMslMm = buffer.getInt(OFFSET_HMSL),
            horizontalAccuracyMm = buffer.getInt(OFFSET_HACC).toLong() and U4_TO_LONG_MASK,
            verticalAccuracyMm = buffer.getInt(OFFSET_VACC).toLong() and U4_TO_LONG_MASK,
            velocityNorthMmPerSec = buffer.getInt(OFFSET_VEL_N),
            velocityEastMmPerSec = buffer.getInt(OFFSET_VEL_E),
            velocityDownMmPerSec = buffer.getInt(OFFSET_VEL_D),
            groundSpeedMmPerSec = buffer.getInt(OFFSET_GSPEED),
            headingDeg = buffer.getInt(OFFSET_HEAD) * HEAD_SCALE,
        )
    }
}
