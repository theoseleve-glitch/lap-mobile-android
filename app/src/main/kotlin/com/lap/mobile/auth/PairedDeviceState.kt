package com.lap.mobile.auth

/**
 * Settings paired-device indicator state (T181).
 *
 * Shape kept open to growth: [Paired.identifier] is nullable today
 * because T189's `/me` profile sync hasn't landed yet. When it does,
 * the indicator can show the email/display-name without changing
 * this surface — `null` means "we know we're paired but haven't
 * fetched the profile yet".
 */
sealed interface PairedDeviceState {
    /** Device has no durable credentials — Settings shows a "Pair this device" CTA. */
    data object NotPaired : PairedDeviceState

    /**
     * Device holds a refresh token. T174 silent-refresh keeps access
     * fresh from this. [identifier] is the user-facing handle (email
     * or display name) when known — null until T189 fetches /me.
     */
    data class Paired(val identifier: String?) : PairedDeviceState
}

/**
 * Maps [SecureTokenStorage] state to [PairedDeviceState] for the
 * Settings indicator (T181).
 *
 * Single source of truth: the resolver derives state from storage
 * rather than tracking a separate isPaired boolean. T175's logout
 * wipes both tokens; T179's redeem writes both; if the indicator
 * had its own state machine, every code path would need to update
 * both, and a forgotten update would lie to the user.
 *
 * Pairing predicate: REFRESH_TOKEN present AND non-blank. Access
 * tokens live ~minutes (T174 rotates them every silent-refresh) so
 * gating on access would make the indicator flicker mid-rotation.
 * Refresh tokens span the device's pairing lifetime, which matches
 * the user's mental model of "is this device paired".
 */
object PairedDeviceStateResolver {
    fun resolve(storage: SecureTokenStorage): PairedDeviceState {
        val refresh = storage.read(SecureTokenKeys.REFRESH_TOKEN)?.takeIf { it.isNotBlank() }
        return if (refresh == null) {
            PairedDeviceState.NotPaired
        } else {
            PairedDeviceState.Paired(identifier = null)
        }
    }
}
