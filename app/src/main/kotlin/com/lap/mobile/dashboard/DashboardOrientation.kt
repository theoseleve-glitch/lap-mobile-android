package com.lap.mobile.dashboard

/**
 * Abstract orientation verdict returned by [OrientationLockPolicy]
 * (T206). The kernel returns one of these values; the Compose
 * layer maps them to [android.content.pm.ActivityInfo] constants
 * when applying the lock to the host Activity.
 *
 * Why an abstract enum, not the Android constant directly: the
 * kernel must stay JVM-pure (no `android.*` imports) so the
 * orientation-decision contract can be JVM-tested. The Compose
 * layer takes the platform-coupling burden — if Android's
 * orientation API surface changes, only the handler ripples;
 * the policy contract stays untouched.
 *
 * Two variants only in v1, both selected by the Recording vs Idle
 * binary lifecycle. New variants (Portrait, ReversePortrait,
 * Locked-current, etc.) get added when actual UX flows need them
 * — premature variants would invite "what should this case do?"
 * questions with no real answer.
 */
enum class DashboardOrientation {
    /**
     * No lock requested — system / user picks via natural
     * rotation. Maps to ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED.
     */
    Unspecified,

    /**
     * Lock to landscape, but allow the device sensor to pick
     * either landscape direction (USB port left or right). Maps
     * to ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE.
     */
    SensorLandscape,
}
