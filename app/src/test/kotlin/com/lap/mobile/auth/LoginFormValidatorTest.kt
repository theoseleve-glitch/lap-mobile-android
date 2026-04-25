package com.lap.mobile.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val VALID_EMAIL: String = "driver@example.com"
private const val VALID_PASSWORD: String = "correct-horse-battery-staple"
private const val WHITESPACE_ONLY: String = "  \t  "

class LoginFormValidatorTest {
    @Test
    fun blank_email_disables_submit_so_user_doesnt_send_an_empty_credential_request() {
        val state = LoginFormState(email = "", password = VALID_PASSWORD)

        assertFalse(LoginFormValidator.canSubmit(state))
    }

    @Test
    fun whitespace_only_email_is_treated_as_blank_so_a_stray_space_doesnt_dispatch_a_login_call() {
        val state = LoginFormState(email = WHITESPACE_ONLY, password = VALID_PASSWORD)

        assertFalse(LoginFormValidator.canSubmit(state))
    }

    @Test
    fun blank_password_disables_submit_so_user_doesnt_send_a_credentialless_request() {
        val state = LoginFormState(email = VALID_EMAIL, password = "")

        assertFalse(LoginFormValidator.canSubmit(state))
    }

    @Test
    fun whitespace_only_password_is_treated_as_blank_so_a_stray_tab_doesnt_dispatch_a_login_call() {
        val state = LoginFormState(email = VALID_EMAIL, password = WHITESPACE_ONLY)

        assertFalse(LoginFormValidator.canSubmit(state))
    }

    @Test
    fun submit_in_flight_disables_submit_so_user_doesnt_double_tap_and_dispatch_two_login_calls() {
        // Without this gate a fast double-tap during a 600ms server round-trip
        // would queue two POST /sign-in calls; Clerk treats them independently
        // and the second one races the first's session creation. Locking the
        // button while isSubmitting is in-flight collapses the user's intent
        // to "exactly once".
        val state = LoginFormState(email = VALID_EMAIL, password = VALID_PASSWORD, isSubmitting = true)

        assertFalse(LoginFormValidator.canSubmit(state))
    }

    @Test
    fun email_password_present_and_no_submit_in_flight_enables_submit() {
        val state = LoginFormState(email = VALID_EMAIL, password = VALID_PASSWORD)

        assertTrue(LoginFormValidator.canSubmit(state))
    }

    @Test
    fun an_error_message_does_not_disable_submit_so_user_can_resubmit_after_correcting() {
        // Common UX failure: a previous failed login leaves an error message
        // hanging in the state; if the validator gated on errorMessage being
        // null, the user could fix the password but never re-submit because
        // the button stayed disabled. Error display is purely advisory.
        val state = LoginFormState(
            email = VALID_EMAIL,
            password = VALID_PASSWORD,
            errorMessage = "Invalid credentials",
        )

        assertTrue(LoginFormValidator.canSubmit(state))
    }
}
