package com.lap.mobile.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import com.lap.mobile.dashboard.RecordingStripState
import com.lap.mobile.dashboard.ScreenWakePolicy

/**
 * Screen-wake handler for the dashboard during a recording
 * session (T205) — keeps the device display on while recording
 * is active so the dashboard stays glance-readable for drivers
 * who watch but don't tap.
 *
 * Why this guard matters: Android's default screen-off timeout
 * is 30s-2min depending on the user's system setting. Drivers
 * glance at the dashboard every ~10s during a session but don't
 * touch it (eyes on road, hands on wheel). Without an active
 * keep-on, the screen would black out between glances, making
 * the live dashboard useless. With the keep-on enabled during
 * recording the screen stays bright; when recording stops the
 * flag clears and normal timeout resumes (preserving battery).
 *
 * Behavior driven by [ScreenWakePolicy.shouldKeepScreenOn]:
 *   - [RecordingStripState.Idle]      → view.keepScreenOn = false
 *     (let system display timeout apply normally).
 *   - [RecordingStripState.Recording] → view.keepScreenOn = true
 *     (override timeout so the dashboard stays awake).
 *
 * Implementation choice: [android.view.View.setKeepScreenOn] on
 * [LocalView.current] rather than [android.view.Window.addFlags]
 * with FLAG_KEEP_SCREEN_ON. Both ultimately set the same window-
 * level flag, but the View-level property:
 *   - is propagated automatically by the Android framework when
 *     a visible view in the hierarchy has it set
 *   - is tied to view lifecycle (no leak if the view goes off-
 *     screen, no manual cleanup-on-Activity-destroy logic)
 *   - works in non-Activity hosts (Fragment, dialog, etc.) without
 *     casting [androidx.compose.ui.platform.LocalContext] to
 *     Activity — that cast is fragile and breaks in Compose
 *     previews and instrumented test harnesses
 *   - is the official Compose recommendation for this exact use
 *     case in the Android docs
 *
 * Why [DisposableEffect] not [androidx.compose.runtime.LaunchedEffect]
 * or [androidx.compose.runtime.SideEffect]: cleanup matters. The
 * contract is "set the flag while [state] mandates it, clear it
 * when this composable leaves composition." LaunchedEffect has no
 * cleanup hook (would leak the flag if the composable is destroyed
 * mid-recording); SideEffect runs on every recomposition with no
 * cleanup. DisposableEffect keys on the policy verdict so the
 * effect re-runs only when the verdict flips, and onDispose
 * guarantees the flag is always cleared even if the composable
 * is unexpectedly destroyed.
 *
 * Renders nothing — this is a side-effect-only composable. No
 * @Preview because there's no visible content; the handler's
 * contract is locked by [ScreenWakePolicy]'s JVM tests, and live
 * device verification of actual screen-stay-awake behavior is
 * [PENDING: device-required].
 */
@Composable
fun RecordingScreenWakeHandler(state: RecordingStripState) {
    val view = LocalView.current
    val keepScreenOn = ScreenWakePolicy.shouldKeepScreenOn(state = state)
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose {
            view.keepScreenOn = false
        }
    }
}
