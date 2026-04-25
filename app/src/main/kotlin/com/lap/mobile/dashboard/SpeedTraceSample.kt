package com.lap.mobile.dashboard

/**
 * One sample on the speed-over-distance trace (T196).
 *
 * Two scalars only — distance traveled (meters) and speed (km/h)
 * at that distance. Decoupled from sensor-side types so the
 * projection kernel + chart Composable can be JVM-tested + previewed
 * without dragging the capture pipeline into the gate.
 *
 * Why distance not time on X: a lap-comparison view (future) needs
 * to overlay multiple laps with corner positions aligned. Time-
 * indexed traces drift apart (one lap is faster, one is slower);
 * distance-indexed traces stay aligned at the same "100m before
 * Turn 3" regardless of lap pace. v1 only renders the last lap so
 * the difference is invisible, but the data shape commits to the
 * scalable choice now.
 */
data class SpeedTraceSample(
    val distanceMeters: Double,
    val speedKmh: Double,
)
