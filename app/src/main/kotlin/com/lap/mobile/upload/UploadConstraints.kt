package com.lap.mobile.upload

import androidx.work.Constraints
import androidx.work.NetworkType
import com.lap.mobile.data.BatteryPolicy
import com.lap.mobile.data.NetworkPolicy

/**
 * Translates the persisted user preferences into the WorkManager
 * [Constraints] the upload [androidx.work.WorkRequest] should carry
 * (T157 network + T158 battery).
 *
 * Network mapping:
 *  - [NetworkPolicy.UNMETERED_ONLY] → [NetworkType.UNMETERED]: the
 *    worker waits for Wi-Fi (or another unmetered link) so a multi-hour
 *    bundle never lands on the user's cellular bill.
 *  - [NetworkPolicy.ALLOW_METERED] → [NetworkType.CONNECTED]: any
 *    connection is fine; the user has explicitly opted into cellular.
 *
 * Battery mapping:
 *  - [BatteryPolicy.DEFER_WHEN_LOW] (the default) → `requiresBatteryNotLow=true`.
 *    WorkManager parks the upload while the device is in the "battery
 *    low" bucket so the transfer doesn't strand a near-empty phone.
 *  - [BatteryPolicy.FORCE_REGARDLESS] → `requiresBatteryNotLow=false`.
 *    User opted to push the upload through even on low battery —
 *    typical at the track when plugged into a tender.
 *
 * Storage constraints (a future task) will extend the same builder so
 * keeping every WorkRequest's pre-flight in one function means the
 * policy can't drift across call sites.
 *
 * `androidx.work.Constraints` is a plain JVM class so this mapping is
 * unit-tested without a device.
 */
fun NetworkPolicy.toUploadConstraints(batteryPolicy: BatteryPolicy = BatteryPolicy.DEFER_WHEN_LOW): Constraints =
    Constraints.Builder()
        .setRequiredNetworkType(
            when (this) {
                NetworkPolicy.UNMETERED_ONLY -> NetworkType.UNMETERED
                NetworkPolicy.ALLOW_METERED -> NetworkType.CONNECTED
            },
        )
        .setRequiresBatteryNotLow(
            when (batteryPolicy) {
                BatteryPolicy.DEFER_WHEN_LOW -> true
                BatteryPolicy.FORCE_REGARDLESS -> false
            },
        )
        .build()
