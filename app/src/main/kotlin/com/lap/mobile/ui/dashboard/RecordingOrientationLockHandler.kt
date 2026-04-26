package com.lap.mobile.ui.dashboard

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.lap.mobile.dashboard.DashboardOrientation
import com.lap.mobile.dashboard.OrientationLockPolicy
import com.lap.mobile.dashboard.RecordingStripState

/**
 * Orientation-lock handler for the dashboard during a recording
 * session (T206) — forces SensorLandscape on the host Activity
 * while recording is active so the dashboard layout (T193-T202
 * widgets) can't re-flow mid-corner from an accidental rotation.
 *
 * Why this guard matters: drivers mount their phone in landscape
 * on dashboard / steering-wheel mounts. The dashboard widgets are
 * sized and arranged for landscape only. A spurious rotation
 * mid-session — caused by a bumpy track, a hand brushing the
 * screen, or a momentarily unmounted phone — would re-flow the
 * layout, hide widgets, and disorient the driver at the worst
 * possible moment. Forcing landscape during recording removes
 * that whole class of failure.
 *
 * Behavior driven by [OrientationLockPolicy.desiredOrientation]:
 *   - [RecordingStripState.Idle]      → orientation cleared
 *     (ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED — let user
 *     rotate naturally; the dashboard is one of several screens).
 *   - [RecordingStripState.Recording] → SCREEN_ORIENTATION_SENSOR_LANDSCAPE
 *     (lock landscape, sensor-decided direction so either USB-
 *     port-left or USB-port-right mount works).
 *
 * Context-chain walker [findActivity] used instead of a direct
 * `LocalContext.current as Activity` cast: the cast is fragile
 * because Compose may host the composable inside a ContextWrapper
 * (themed context, ContextThemeWrapper, FragmentHost, etc.). The
 * walker traverses baseContext until it finds an Activity, or
 * returns null if there isn't one (e.g., in a Compose preview
 * harness). Null-safe — if no Activity host is found the orientation
 * change is silently skipped, which is exactly the right behavior
 * for previews and instrumented test harnesses.
 *
 * [DisposableEffect] keyed on the orientation enum: re-runs only
 * when the verdict flips. onDispose always clears back to
 * UNSPECIFIED — simpler than capture-and-restore-previous because
 * the manifest doesn't declare an orientation lock so UNSPECIFIED
 * IS the natural default. Cleanup runs even if the composable is
 * unexpectedly destroyed mid-recording (back-stack pop, configuration
 * change, etc.) so a stale lock can't leak into other screens.
 *
 * Renders nothing — side-effect-only composable. No @Preview
 * because there's no visible content; the contract is locked by
 * [OrientationLockPolicy]'s JVM tests, and live device verification
 * of actual orientation lock + restore behavior is [PENDING:
 * device-required].
 */
@Composable
fun RecordingOrientationLockHandler(state: RecordingStripState) {
    val context = LocalContext.current
    val orientation = OrientationLockPolicy.desiredOrientation(state = state)
    DisposableEffect(orientation) {
        val activity = context.findActivity()
        activity?.requestedOrientation = orientation.toScreenOrientation()
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun DashboardOrientation.toScreenOrientation(): Int = when (this) {
    DashboardOrientation.Unspecified -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    DashboardOrientation.SensorLandscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
}
