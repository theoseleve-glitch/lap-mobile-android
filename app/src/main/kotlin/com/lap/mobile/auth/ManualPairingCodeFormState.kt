package com.lap.mobile.auth

/**
 * UiState for the manual pairing code entry screen (T180) — fallback
 * when camera-based QR scanning (T178) isn't usable.
 *
 * Stateless from the Composable's perspective: the screen renders
 * exactly what's in this data class. The hosting ViewModel mutates it
 * in response to user input + the response of T179's
 * [PairingRedeemUseCase].
 *
 * [errorMessage] is advisory and does NOT gate submission (see
 * [ManualPairingCodeFormValidator]) — fixing a typo after a
 * server-rejected attempt should re-enable the button immediately
 * rather than requiring the user to first dismiss the error.
 *
 * [isSubmitting] is the only "gate" property; it locks the submit
 * button while a redeem call is in flight so a fast double-tap
 * collapses to a single network round trip.
 */
data class ManualPairingCodeFormState(
    val draft: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Decides whether the submit button is enabled. Mirrors T172's
 * [LoginFormValidator] shape so both auth entry points share the
 * same UX behavior — a user who used Login yesterday and Manual
 * Pairing today shouldn't see two different submit-gate behaviors.
 *
 * Format validation lives server-side (see
 * [ManualPairingCodeFormStateValidator] doc above) — pre-validating
 * the token shape client-side would just mask the canonical check
 * and grow stale as the token format evolves.
 */
object ManualPairingCodeFormValidator {
    fun canSubmit(state: ManualPairingCodeFormState): Boolean = state.draft.isNotBlank() && !state.isSubmitting
}
