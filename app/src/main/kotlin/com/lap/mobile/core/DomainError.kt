package com.lap.mobile.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Closed error vocabulary every repository maps into (CLAUDE.md: "Every
 * repository returns Result<T, DomainError>"). Consumers pattern-match
 * exhaustively.
 *
 * Rationale for the variant set:
 * - Io / Network / Serialization — infrastructure boundaries we cross in
 *   capture, upload, and JSON paths (ADR-012 ingest pipeline).
 * - NotFound — 404-class lookups (sessions, tracks, rules bundles).
 * - Unauthorized — Clerk token missing/expired (ADR-011).
 * - Validation — client-side preflight failures (Block E).
 * - Unknown — catch-all; further blocks may add specific variants.
 *
 * Throwable fields are intentionally absent: errors must be JSON-stable
 * so they can be logged, cached, and surfaced in UI without custom
 * serializers.
 */
@Serializable
sealed class DomainError {
    @Serializable
    @SerialName("io")
    data class Io(val message: String) : DomainError()

    @Serializable
    @SerialName("network")
    data class Network(val status: Int? = null, val message: String) : DomainError()

    @Serializable
    @SerialName("serialization")
    data class Serialization(val message: String) : DomainError()

    @Serializable
    @SerialName("not_found")
    data class NotFound(val what: String) : DomainError()

    @Serializable
    @SerialName("unauthorized")
    data class Unauthorized(val reason: String? = null) : DomainError()

    @Serializable
    @SerialName("validation")
    data class Validation(val field: String? = null, val message: String) : DomainError()

    @Serializable
    @SerialName("unknown")
    data object Unknown : DomainError()
}
