package com.lap.mobile.capture.ble

/**
 * Parsed UBX-MON-VER payload (T116). The receiver's firmware
 * identification — populates `provenance.firmware` in the bundle
 * header per ADR-012 ingestion shape so the server can correlate
 * a session against known firmware quirks.
 *
 * [softwareVersion] / [hardwareVersion] are the spec's null-
 * terminated 30 / 10-byte fields, trimmed; [extensions] is the
 * variable-length tail (each 30 bytes, null-terminated) carrying
 * vendor extensions like protocol versions.
 */
data class MonVer(
    val softwareVersion: String,
    val hardwareVersion: String,
    val extensions: List<String>,
)
