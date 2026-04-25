package com.lap.mobile.capture.ble

/**
 * Single ESF-MEAS measurement entry — same data + type packing as
 * [EsfRawMeasurement] (24-bit signed value + 8-bit type) but
 * without a per-measurement time tag (the parent [EsfMeas] carries
 * a single shared `timeTag`).
 */
data class EsfMeasMeasurement(
    val dataType: Int,
    val dataValue: Int,
)

/**
 * Parsed UBX-ESF-MEAS payload (T113). Variable-length;
 * post-fusion sensor measurements emitted as a batch sharing one
 * sensor time tag. Optional `calibTtag` trailing word is surfaced
 * via [calibTimeTag] when present, `null` otherwise — the
 * `flags` bit-3 advertises whether it's there but length-only
 * inspection is more robust against firmware quirks.
 */
data class EsfMeas(
    val timeTag: Long,
    val flags: Int,
    val id: Int,
    val measurements: List<EsfMeasMeasurement>,
    val calibTimeTag: Long?,
)
