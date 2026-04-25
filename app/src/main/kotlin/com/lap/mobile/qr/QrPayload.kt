package com.lap.mobile.qr

import java.net.URI

/**
 * Classified shape of a scanned QR-code payload (T178). The camera
 * shell hands the raw decoded `String` to [QrPayloadClassifier]; the
 * resulting [QrPayload] is what T179's pairing flow + future deep-link
 * handlers consume.
 *
 * Variants kept tight to known-good shapes — anything we don't
 * recognize collapses to [Unknown] rather than fabricating a guess.
 * Misclassifying a random QR (Wi-Fi config, vCard, blog URL) as a
 * pairing token would POST garbage to `/auth/pairing/redeem` and
 * surface a confusing 400 to the user.
 */
sealed interface QrPayload {
    /** A pairing token from the webapp's "pair this device" QR. T179 redeems via POST. */
    data class PairingToken(val token: String) : QrPayload

    /** Anything else — scanner saw a barcode, but it wasn't a shape we can route. */
    data object Unknown : QrPayload
}

/**
 * Pure-Kotlin classifier — no Android, no ML Kit; consumes the
 * `String` ML Kit produces and decides what it represents.
 *
 * Two pairing-URL shapes accepted, both routes the webapp can emit:
 *   - `lap://pair?token=…` (custom scheme — works as a deep link via
 *     T184's intent filter)
 *   - `https://app.lap-coach.racing/pair?token=…` (universal App
 *     Link — same shape T185 will register)
 *
 * Token must be non-blank — a trailing `?token=` with no value comes
 * back as [QrPayload.Unknown] rather than producing a `PairingToken("")`
 * that would POST `{ "token": "" }` to the server.
 *
 * Malformed URIs are tolerated: `URI.create` throws
 * `IllegalArgumentException` on garbage like `not a uri ::: %%%`, and
 * we want a fall-through to [QrPayload.Unknown] rather than tunnelling
 * the exception up to the camera screen.
 */
object QrPayloadClassifier {
    private const val LAP_SCHEME: String = "lap"
    private const val LAP_PAIR_HOST: String = "pair"
    private const val UNIVERSAL_LINK_HOST: String = "app.lap-coach.racing"
    private const val UNIVERSAL_LINK_PAIR_PATH: String = "/pair"
    private const val HTTPS_SCHEME: String = "https"
    private const val TOKEN_QUERY_KEY: String = "token"

    fun classify(rawScanned: String?): QrPayload {
        val token =
            rawScanned
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { URI.create(it) }.getOrNull() }
                ?.takeIf { it.isPairingRoute() }
                ?.queryParam(TOKEN_QUERY_KEY)
                ?.takeIf { it.isNotBlank() }
        return token?.let { QrPayload.PairingToken(it) } ?: QrPayload.Unknown
    }

    private fun URI.isPairingRoute(): Boolean = (scheme == LAP_SCHEME && host == LAP_PAIR_HOST) ||
        (scheme == HTTPS_SCHEME && host == UNIVERSAL_LINK_HOST && path == UNIVERSAL_LINK_PAIR_PATH)

    private fun URI.queryParam(name: String): String? = query
        ?.split("&")
        ?.firstNotNullOfOrNull { pair ->
            val eq = pair.indexOf('=')
            if (eq < 0) return@firstNotNullOfOrNull null
            if (pair.substring(0, eq) != name) return@firstNotNullOfOrNull null
            pair.substring(eq + 1)
        }
}
