package com.lap.mobile.capture

/**
 * Tiny seam over `android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.isEnabled`
 * so [BluetoothCheck] (T089) stays JVM-testable without Robolectric
 * or the real `BluetoothManager`. The Android-side wrapper is a
 * one-liner that the foreground service / Hilt module supplies.
 */
fun interface BluetoothAdapterStatus {
    fun isEnabled(): Boolean
}
