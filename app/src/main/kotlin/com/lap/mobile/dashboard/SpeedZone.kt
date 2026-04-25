package com.lap.mobile.dashboard

/**
 * Speed-band classification for the dashboard gauge (T193).
 *
 * Four bands, each rendered in a distinct color by
 * [com.lap.mobile.ui.dashboard.SpeedGauge]:
 *   - [Cool]    pit lane, paddock, slowest corners
 *   - [Cruise]  warmup laps, slow sections
 *   - [Fast]    active racing, most straights
 *   - [Redline] top-speed straights
 *
 * Why an enum, not just a Color: the band drives more than the fill
 * color — a future tweak might also drive label visibility (e.g.,
 * "REDLINE" badge), animation (pulse on Redline), or haptic feedback.
 * Naming the bands at the kernel keeps those downstream concerns
 * cohesive instead of strewing magic colors through the Compose code.
 */
enum class SpeedZone {
    Cool,
    Cruise,
    Fast,
    Redline,
}

/**
 * Pure function: speed (km/h) → [SpeedZone] (T193 kernel).
 *
 * Thresholds (v1 default for circuit racing):
 *   - [COOL_TO_CRUISE_KMH]   = 50
 *   - [CRUISE_TO_FAST_KMH]   = 100
 *   - [FAST_TO_REDLINE_KMH]  = 150
 *
 * Boundary semantics: strict-less-than at the upper edge of each
 * band, so a speed of exactly 50 lands in [SpeedZone.Cruise] (not
 * [SpeedZone.Cool]). Matches drivers' mental model — "I crossed 50"
 * means the next band engaged.
 *
 * Negative speeds defensively collapse to [SpeedZone.Cool] — GPS
 * speed is never negative in practice (CompositeSpeedFilter clamps),
 * but if a future data path emits a stray -0.5 from NaN-ish drift,
 * the gauge must not crash or render an invalid zone.
 *
 * Thresholds are file-private constants because v1 has one set of
 * bands. If a future track-class-aware variant lands (karting vs
 * GP-circuit have very different "fast" cutoffs), the classifier
 * grows a `bandsForTrackClass(...)` factory rather than mutating
 * these globals.
 */
object SpeedZoneClassifier {
    fun classify(speedKmh: Double): SpeedZone = when {
        speedKmh < COOL_TO_CRUISE_KMH -> SpeedZone.Cool
        speedKmh < CRUISE_TO_FAST_KMH -> SpeedZone.Cruise
        speedKmh < FAST_TO_REDLINE_KMH -> SpeedZone.Fast
        else -> SpeedZone.Redline
    }

    private const val COOL_TO_CRUISE_KMH: Double = 50.0
    private const val CRUISE_TO_FAST_KMH: Double = 100.0
    private const val FAST_TO_REDLINE_KMH: Double = 150.0
}
