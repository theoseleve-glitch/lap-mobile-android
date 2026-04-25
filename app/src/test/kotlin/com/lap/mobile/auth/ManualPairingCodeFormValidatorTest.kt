package com.lap.mobile.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val VALID_CODE: String = "p_01JX9Z8M4KTRP5D6Y3V2N7H8KQ"
private const val WHITESPACE_ONLY: String = "  \t  "

/**
 * Manual pairing code entry (T180) — fallback for users whose camera
 * doesn't work or who can't aim at a QR (low-light, broken lens, Wear
 * OS device, accessibility need). Same submit-gate semantics as
 * T172's [LoginFormValidator]:
 *   - draft non-blank AND not currently submitting → submit enabled
 *   - error message is purely advisory; doesn't gate so user can fix
 *     a typo and resubmit without re-tapping the field
 *
 * Format validation deliberately NOT here — the canonical validator
 * is the backend at `/auth/pairing/redeem`. The pairing token shape
 * is opaque (the QR encodes it as `p_01JX9Z…` ULIDs today, but
 * future server changes could rotate the prefix). Locking format
 * client-side would just turn into a maintenance liability.
 */
class ManualPairingCodeFormValidatorTest {
    @Test
    fun blank_draft_disables_submit_so_user_doesnt_post_an_empty_pairing_token() {
        val state = ManualPairingCodeFormState(draft = "")

        assertFalse(ManualPairingCodeFormValidator.canSubmit(state))
    }

    @Test
    fun whitespace_only_draft_is_treated_as_blank_so_a_stray_paste_doesnt_dispatch_a_redeem_call() {
        val state = ManualPairingCodeFormState(draft = WHITESPACE_ONLY)

        assertFalse(ManualPairingCodeFormValidator.canSubmit(state))
    }

    @Test
    fun submit_in_flight_disables_submit_so_user_doesnt_double_tap_and_dispatch_two_redeem_calls() {
        // Same problem as T172 login: a fast double-tap during the
        // 600ms server round-trip would queue two POST /auth/pairing/redeem
        // calls. Server is supposed to be idempotent on the same token,
        // but the second call still wastes a network round trip and
        // potentially races the first response into storage.
        val state = ManualPairingCodeFormState(draft = VALID_CODE, isSubmitting = true)

        assertFalse(ManualPairingCodeFormValidator.canSubmit(state))
    }

    @Test
    fun draft_present_and_no_submit_in_flight_enables_submit() {
        val state = ManualPairingCodeFormState(draft = VALID_CODE)

        assertTrue(ManualPairingCodeFormValidator.canSubmit(state))
    }

    @Test
    fun an_error_message_does_not_disable_submit_so_user_can_fix_a_typo_and_retry() {
        // After a server rejection (T179's TokenRejected outcome),
        // errorMessage holds the user-facing string. If the validator
        // gated on errorMessage being null, the user could fix one
        // character but never re-submit because the button stayed
        // disabled until they cleared the error themselves. Error
        // display is purely advisory.
        val state =
            ManualPairingCodeFormState(
                draft = VALID_CODE,
                errorMessage = "That code isn't valid",
            )

        assertTrue(ManualPairingCodeFormValidator.canSubmit(state))
    }
}
