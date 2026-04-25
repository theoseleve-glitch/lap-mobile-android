package com.lap.mobile.capture.ble

/**
 * Parsed UBX-SEC-UNIQID payload (T115). Carries the receiver's
 * 40-bit unique chip ID, surfaced as a lowercase hex string so it
 * round-trips into the bundle's `provenance.device_id` field per
 * ADR-012 ingestion shape.
 *
 * The hex form is canonicalised here (no `0x` prefix, lowercase,
 * fixed 10-character width with leading zeros) so downstream
 * comparisons against bundle headers are byte-stable across
 * firmware revs that might inadvertently uppercase or pad
 * differently.
 */
data class SecUniqId(
    val version: Int,
    val deviceIdHex: String,
)
