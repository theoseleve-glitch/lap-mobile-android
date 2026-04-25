package com.lap.mobile.capture

private const val ANDROID_API_S: Int = 31

/**
 * Preflight check for the BLE leg of a RaceBox capture session
 * (T089). Only fires when [raceboxModeEnabled] is `true` —
 * iRacing-only or pure-mobile-GNSS sessions don't need BT, so we
 * shouldn't surface a redundant blocker.
 *
 * Within RaceBox mode there are two failure modes, in priority
 * order:
 *
 *  1. **Permission denied** — Android 12 (S, API 31+) split the old
 *     install-time `BLUETOOTH` permission into runtime
 *     [BLUETOOTH_CONNECT] (and `BLUETOOTH_SCAN`); without
 *     `BLUETOOTH_CONNECT` we can't open a GATT session at all.
 *     Pre-S devices still rely on the install-time legacy permission
 *     and don't need a runtime grant.
 *  2. **Adapter disabled** — even with the permission, the user can
 *     have BT turned off in settings; the foreground service can
 *     prompt-to-enable but not silently flip it.
 *
 * Permission takes priority over adapter state because the
 * remediation flow ("grant permission") is a strict prerequisite of
 * the next one ("enable BT") — Android's permission dialog precedes
 * any BT enable intent.
 */
class BluetoothCheck(
    private val checker: PermissionChecker,
    private val adapter: BluetoothAdapterStatus,
    private val sdkInt: Int,
    private val raceboxModeEnabled: () -> Boolean,
) : PreflightCheck {
    override suspend fun failureReason(): String? {
        if (!raceboxModeEnabled()) return null
        if (sdkInt >= ANDROID_API_S && !checker.isGranted(BLUETOOTH_CONNECT)) {
            return REASON_BLUETOOTH_PERMISSION
        }
        if (!adapter.isEnabled()) return REASON_BLUETOOTH_DISABLED
        return null
    }

    companion object {
        const val REASON_BLUETOOTH_PERMISSION: String = "bluetooth_permission"
        const val REASON_BLUETOOTH_DISABLED: String = "bluetooth_disabled"
        const val BLUETOOTH_CONNECT: String = "android.permission.BLUETOOTH_CONNECT"
    }
}
