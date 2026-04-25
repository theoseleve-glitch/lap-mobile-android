package com.lap.mobile.account

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Server-side user profile (T189).
 *
 * Returned by `GET /me` after authentication. The mobile cache
 * holds at most one of these (the active user's) so the paired-
 * device indicator and account chooser can show the human-readable
 * handle instead of T187's placeholder `AccountId("active")`.
 *
 * `@Serializable` so the production [MeProfileFetcher] HTTP impl
 * can deserialize the JSON body directly via Retrofit's
 * kotlinx-serialization converter.
 *
 * `@SerialName` annotations enforce the snake_case wire format
 * regardless of how Kotlin field names drift — the server uses
 * `display_name`, this codebase uses `displayName`. Without the
 * annotation a typo on the server side would silently null out
 * the field (kotlinx-serialization is lenient about missing
 * fields by default for nullable types).
 *
 * `id` is the real server-side user identifier — replaces T187's
 * placeholder when consumers eventually wire through the cache.
 *
 * `displayName` nullable because some users sign up with email
 * only and never set a display name; UI falls back to the email.
 */
@Serializable
data class MeProfile(
    @SerialName("id") val id: String,
    @SerialName("email") val email: String,
    @SerialName("display_name") val displayName: String?,
)
