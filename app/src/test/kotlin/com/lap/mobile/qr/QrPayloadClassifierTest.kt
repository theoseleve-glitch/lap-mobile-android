package com.lap.mobile.qr

import org.junit.Assert.assertEquals
import org.junit.Test

private const val PAIRING_TOKEN: String = "p_01JX9Z8M4KTRP5D6Y3V2N7H8KQ"
private const val OTHER_TOKEN: String = "x_01JX9Z8M4KTRP5D6Y3V2N7H8KQ"

/**
 * JVM-testable kernel for classifying raw QR-decoded text (T178).
 *
 * The actual ML Kit / CameraX scan loop is [PENDING: device-required] —
 * the camera surface only meaningfully runs on a device. What the
 * scanner produces, however, is a `String` (the decoded payload), and
 * what the rest of the app does with that string is entirely
 * pure-Kotlin: classify it into a known shape so T179's pairing flow
 * can `when (payload) { is PairingToken -> redeem(token); Unknown ->
 * showError(); }` without re-doing the URL parsing.
 *
 * Two shapes accepted now (both routes the webapp can emit, and both
 * the same routes T184/T185 will register as App Links):
 *   - `lap://pair?token=…` (custom scheme, deep-link)
 *   - `https://app.lap-coach.racing/pair?token=…` (universal App Link)
 * Anything else collapses to [QrPayload.Unknown] — defensive against
 * the user pointing the scanner at a random QR (Wi-Fi, vCard, URL).
 */
class QrPayloadClassifierTest {
    @Test
    fun null_input_classifies_as_unknown_so_a_pre_first_scan_state_doesnt_crash_the_screen() {
        // ML Kit's barcode result list can be empty; the camera shell
        // would emit null for "no barcode visible right now". Classifier
        // must tolerate that without throwing.
        assertEquals(QrPayload.Unknown, QrPayloadClassifier.classify(null))
    }

    @Test
    fun empty_input_classifies_as_unknown_so_a_blank_decoded_payload_isnt_misread_as_a_token() {
        assertEquals(QrPayload.Unknown, QrPayloadClassifier.classify(""))
    }

    @Test
    fun whitespace_only_input_classifies_as_unknown() {
        assertEquals(QrPayload.Unknown, QrPayloadClassifier.classify("   \t\n  "))
    }

    @Test
    fun lap_pair_url_with_token_param_classifies_as_pairing_token_and_extracts_the_value() {
        val payload = QrPayloadClassifier.classify("lap://pair?token=$PAIRING_TOKEN")

        assertEquals(QrPayload.PairingToken(PAIRING_TOKEN), payload)
    }

    @Test
    fun https_app_lap_coach_racing_pair_url_classifies_as_pairing_token_for_app_links_universality() {
        // The same URL shape that T184/T185 will configure as a universal
        // App Link — scanning the QR or tapping the link should land in
        // the same code path. Locking the parser on both forms keeps that
        // promise.
        val payload = QrPayloadClassifier.classify("https://app.lap-coach.racing/pair?token=$PAIRING_TOKEN")

        assertEquals(QrPayload.PairingToken(PAIRING_TOKEN), payload)
    }

    @Test
    fun lap_pair_url_with_extra_query_params_still_extracts_the_named_token() {
        // Future-proofing: backend may add tracking params (utm_source,
        // ref) or device-binding params alongside `token`. Parser must
        // pluck the named one rather than assume position.
        val payload =
            QrPayloadClassifier.classify(
                "lap://pair?utm_source=email&token=$PAIRING_TOKEN&ref=onboarding",
            )

        assertEquals(QrPayload.PairingToken(PAIRING_TOKEN), payload)
    }

    @Test
    fun lap_pair_url_with_blank_token_value_classifies_as_unknown_not_pairing_with_empty_string() {
        // Defensive: a backend bug or QR-generator edge case could emit
        // `lap://pair?token=` with an empty value. Don't claim that's a
        // valid pairing token — it would POST `{ "token": "" }` to the
        // server and surface a confusing 400.
        assertEquals(QrPayload.Unknown, QrPayloadClassifier.classify("lap://pair?token="))
    }

    @Test
    fun lap_pair_url_with_no_token_param_classifies_as_unknown() {
        assertEquals(QrPayload.Unknown, QrPayloadClassifier.classify("lap://pair?other=$OTHER_TOKEN"))
    }

    @Test
    fun unrelated_lap_scheme_url_with_different_host_classifies_as_unknown() {
        // Forward-compat: `lap://settings?theme=dark` shouldn't be
        // mistaken for a pairing payload just because the scheme matches.
        assertEquals(
            QrPayload.Unknown,
            QrPayloadClassifier.classify("lap://settings?token=$PAIRING_TOKEN"),
        )
    }

    @Test
    fun unrelated_https_url_classifies_as_unknown_so_a_random_QR_pointing_at_a_blog_doesnt_misfire() {
        assertEquals(
            QrPayload.Unknown,
            QrPayloadClassifier.classify("https://example.com/pair?token=$PAIRING_TOKEN"),
        )
    }

    @Test
    fun plain_text_classifies_as_unknown_so_pointing_camera_at_a_recipe_card_doesnt_crash() {
        // ML Kit also decodes Wi-Fi QRs, vCards, plain text, etc.
        // Classifier must reject anything that isn't our pairing shape.
        assertEquals(QrPayload.Unknown, QrPayloadClassifier.classify("Just some random text"))
    }

    @Test
    fun malformed_uri_classifies_as_unknown_without_throwing() {
        // ML Kit can occasionally emit garbage payloads (compressed QR
        // misread, partial decode). Parser must fall back to Unknown
        // rather than tunnel an IllegalArgumentException up to the UI.
        assertEquals(QrPayload.Unknown, QrPayloadClassifier.classify("not a uri at all ::: %%% [["))
    }
}
