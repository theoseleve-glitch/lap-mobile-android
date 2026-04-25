package com.lap.mobile.capture.ble

/**
 * Single advertising RaceBox device surfaced by the BLE scan
 * (T102). [address] is the MAC (also the user-facing identifier
 * since RaceBox's own naming convention encodes the unit's serial
 * in the broadcast name); [name] is the broadcast name from the
 * advertising packet, `null` if the peripheral didn't include one;
 * [rssiDbm] is the raw signal strength so the picker UI (T123)
 * can sort or filter the list.
 */
data class RaceboxAdvertiser(
    val address: String,
    val name: String?,
    val rssiDbm: Int,
)
