package com.lap.mobile.capture

import com.lap.schemas.v1.TelemetrySample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * Combines location + motion (+ any later source like BLE / RaceBox)
 * into a single `Flow<TelemetrySample>` (T071). Each upstream is
 * mapped to the wire shape *before* `merge` so the per-sample
 * `tSourceClock` tag (`gnss` for location, `device_monotonic` for IMU)
 * sticks to its origin — `merge` interleaves but never fuses, in
 * keeping with ADR-007's "no on-device fusion" rule (T072 expands the
 * tagging contract).
 *
 * `additionalSources` is the slot for streams the orchestrator
 * doesn't own a typed wrapper for yet — Block F BLE (T108+) plugs in
 * here once it produces `TelemetrySample`s directly. Pre-mapped so
 * the orchestrator stays agnostic about how each source looks
 * upstream.
 *
 * Uses `Iterable<Flow<T>>.merge` to side-step detekt's SpreadOperator
 * rule on `*flows.toTypedArray()` (per the saved feedback memory).
 */
class CaptureOrchestrator(
    private val location: LocationCaptureService,
    private val motion: MotionCaptureService,
    private val additionalSources: List<Flow<TelemetrySample>> = emptyList(),
) {
    fun samples(): Flow<TelemetrySample> {
        val builtIn: List<Flow<TelemetrySample>> = listOf(
            location.samples().map { it.toTelemetrySample() },
            motion.samples().map { it.toTelemetrySample() },
        )
        return (builtIn + additionalSources).merge()
    }
}
