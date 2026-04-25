package com.lap.mobile.capture.ble

/**
 * Parsed UBX-NAV-RELPOSNED payload (T110). Used by RTK-capable
 * RaceBox firmware to report the rover-to-base baseline; lap
 * detection doesn't strictly need it but keeping the parser around
 * lets a future RTK-precision coaching mode (sub-cm corner-apex
 * tracking) consume the same wire stream without re-decoding.
 *
 * Distance fields stay in cm (raw spec is cm), heading is
 * pre-converted to degrees (raw is degrees × 1e-5) — same
 * convention as [NavPvt.headingDeg]. The "high-precision" tail
 * fields (mm-scale corrections) are intentionally absent from this
 * record because the consumer-grade pipeline doesn't surface them
 * yet; T117's mapper can add them when an RTK channel ships.
 */
data class NavRelPosNed(
    val version: Int,
    val refStationId: Int,
    val iTOW: Long,
    val relPosNorthCm: Int,
    val relPosEastCm: Int,
    val relPosDownCm: Int,
    val relPosLengthCm: Int,
    val relPosHeadingDeg: Double,
    val flags: Long,
)
