package com.lap.mobile.auth

/**
 * UiState for the login screen (T172).
 *
 * Stateless from the Composable's perspective — the screen renders
 * exactly what's in this data class, and the future ViewModel /
 * Clerk-backed use case (Block I post-toolchain-bump) is responsible
 * for mutating it in response to user input + server responses.
 *
 * [errorMessage] is purely advisory and doesn't gate the submit
 * button (see [LoginFormValidator]). A user who fixed a typo after a
 * failed login should be able to resubmit immediately; locking them
 * out until the error string is cleared would be hostile UX.
 *
 * [isSubmitting] is the only "gate" property — when an in-flight
 * sign-in call hasn't returned yet, the submit button must be disabled
 * to collapse rapid double-taps to a single intent.
 */
data class LoginFormState(
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Pure-Kotlin "is the submit button enabled?" decider for the login
 * screen (T172). Locks the rule in one place so the Composable, the
 * future ViewModel, and JVM tests all agree on what counts as
 * submittable.
 *
 * Rule: both fields non-blank AND no submit currently in flight.
 * Email format validation is intentionally NOT in scope here — Clerk
 * (or our backend) returns its own format error, which lands in
 * [LoginFormState.errorMessage] for display. Locking format rules
 * client-side would only mask the canonical server-side check.
 */
object LoginFormValidator {
    fun canSubmit(state: LoginFormState): Boolean =
        state.email.isNotBlank() && state.password.isNotBlank() && !state.isSubmitting
}
