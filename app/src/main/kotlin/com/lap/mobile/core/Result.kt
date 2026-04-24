package com.lap.mobile.core

/**
 * Success / failure carrier used as the canonical return type for every
 * repository and interactor (CLAUDE.md: "Every repository returns
 * Result<T, DomainError>"). Two-parameter so callers can vary the error
 * vocabulary when needed; typical shape is `Result<T, DomainError>`.
 *
 * NOT @Serializable: kotlinx-serialization 1.7.x can't auto-generate
 * correct serializers for generic sealed classes with unbounded `out T`
 * — the subclass's type arg erases to `Any`, which forces polymorphic
 * dispatch that fails at runtime. The Result wrapper is a transient
 * in-memory type; the success value or DomainError inside are themselves
 * serializable when needed.
 *
 * NOT a typealias to kotlin.Result — that's Throwable-based and has a
 * single type parameter.
 */
sealed class Result<out T, out E> {
    data class Ok<out T>(val value: T) : Result<T, Nothing>()
    data class Err<out E>(val error: E) : Result<Nothing, E>()
}
