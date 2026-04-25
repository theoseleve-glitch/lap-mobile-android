package com.lap.mobile.capture.ble

/**
 * Parsed UBX-NAV-STATUS payload (T108). Lighter-weight than
 * NAV-PVT; useful for the connection-health UI (T123) and
 * forensic-audit views that want fix-state evolution without
 * the per-sample position cost.
 */
data class NavStatus(
    val iTOW: Long,
    val gpsFix: Int,
    val flags: Int,
    val fixStat: Int,
    val flags2: Int,
    val timeToFirstFixMs: Long,
    val millisSinceStartup: Long,
)
