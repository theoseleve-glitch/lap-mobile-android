package com.lap.mobile.ui.ble

private const val EXCELLENT_THRESHOLD_DBM: Int = -60
private const val GOOD_THRESHOLD_DBM: Int = -75
private const val FAIR_THRESHOLD_DBM: Int = -90

/**
 * 4-bucket RSSI categorisation for the BLE picker UI (T123).
 * Thresholds picked from typical 2.4 GHz BLE link-budget guidance:
 * 　- ≥ -60 dBm  → [EXCELLENT] — same room / line-of-sight
 * 　- ≥ -75 dBm  → [GOOD]      — through one wall, reliable streaming
 * 　- ≥ -90 dBm  → [FAIR]      — fringe; intermittent drops likely
 * 　- otherwise  → [POOR]      — pair will probably fail
 *
 * Pure / Android-free so the bucket logic is JVM-tested; the UI maps
 * the bucket to an icon/colour ramp at render time.
 */
enum class RssiSignalLevel {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
    ;

    companion object {
        fun fromDbm(rssiDbm: Int): RssiSignalLevel = when {
            rssiDbm >= EXCELLENT_THRESHOLD_DBM -> EXCELLENT
            rssiDbm >= GOOD_THRESHOLD_DBM -> GOOD
            rssiDbm >= FAIR_THRESHOLD_DBM -> FAIR
            else -> POOR
        }
    }
}
