package com.lap.mobile.capture

/**
 * Live RaceBox BLE connection state for [RaceboxConnectionCheck]
 * (T092). The actual GATT plumbing lands in Block F (T101+); for the
 * preflight gate here we only need a `connected vs not` answer, kept
 * minimal so the check is JVM-testable today and the Block F service
 * can implement the seam unchanged.
 */
fun interface RaceboxConnectionStatus {
    suspend fun isConnected(): Boolean
}
