package com.lap.mobile.capture

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Production [PermissionChecker] backed by `ContextCompat.checkSelfPermission`.
 * Bound by the Hilt module under [Context]; tests do not exercise this
 * class — they substitute a fake [PermissionChecker].
 */
class ContextPermissionChecker(private val context: Context) : PermissionChecker {
    override fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
