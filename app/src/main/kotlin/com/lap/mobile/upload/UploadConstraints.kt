package com.lap.mobile.upload

import androidx.work.Constraints
import androidx.work.NetworkType
import com.lap.mobile.data.NetworkPolicy

/**
 * Translates the persisted [NetworkPolicy] into the WorkManager
 * [Constraints] the upload [androidx.work.WorkRequest] should carry
 * (T157).
 *
 *  - [NetworkPolicy.UNMETERED_ONLY] → [NetworkType.UNMETERED]: the
 *    worker waits for Wi-Fi (or another unmetered link) so a multi-hour
 *    bundle never lands on the user's cellular bill.
 *  - [NetworkPolicy.ALLOW_METERED] → [NetworkType.CONNECTED]: any
 *    connection is fine; the user has explicitly opted into cellular.
 *
 * Battery + storage constraints (T158+) extend the same builder when
 * those tasks ship — keeping every WorkRequest's pre-flight in one
 * function means the policy can't drift across call sites.
 *
 * `androidx.work.Constraints` is a plain JVM class so this mapping is
 * unit-tested without a device.
 */
fun NetworkPolicy.toUploadConstraints(): Constraints = Constraints.Builder()
    .setRequiredNetworkType(
        when (this) {
            NetworkPolicy.UNMETERED_ONLY -> NetworkType.UNMETERED
            NetworkPolicy.ALLOW_METERED -> NetworkType.CONNECTED
        },
    )
    .build()
