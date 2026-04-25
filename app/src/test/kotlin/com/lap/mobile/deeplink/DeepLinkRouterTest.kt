package com.lap.mobile.deeplink

import org.junit.Assert.assertEquals
import org.junit.Test

private const val PAIRING_TOKEN: String = "p_01JX9Z8M4KTRP5D6Y3V2N7H8KQ"
private const val OTHER_TOKEN: String = "x_arbitrary"

/**
 * Locks the T184 deep-link routing contract: an inbound URL handed to
 * the app via [android.content.Intent.getData] maps to a structured
 * [DeepLinkAction] that the activity layer can `when`-dispatch.
 *
 * The router composes T178's
 * [com.lap.mobile.qr.QrPayloadClassifier] for the pairing-URL shape so
 * a webapp-emitted `lap://pair?token=…` link and a scanned QR with
 * the same payload land at the same parsing logic — change the URL
 * format once, both consumers update.
 *
 * [DeepLinkAction] is deliberately NOT [QrPayload] because deep links
 * have a broader future domain: `lap://share?session=…`,
 * `lap://settings/notifications`, etc. Keeping the router's surface
 * its own type lets it grow without polluting the QR parser's
 * tighter domain.
 */
class DeepLinkRouterTest {
    @Test
    fun lap_pair_url_with_token_routes_to_pair_action_carrying_the_extracted_token() {
        val action = DeepLinkRouter.route("lap://pair?token=$PAIRING_TOKEN")

        assertEquals(DeepLinkAction.Pair(PAIRING_TOKEN), action)
    }

    @Test
    fun https_app_link_pair_url_also_routes_to_pair_action_for_T185_universal_links() {
        // T185 lands assetlinks.json verification on
        // app.lap-coach.racing/pair so taps on the webapp's link land
        // in the same activity. The router must accept both shapes —
        // the activity intent filter declares both, and this test
        // locks they both produce the same Pair action.
        val action = DeepLinkRouter.route("https://app.lap-coach.racing/pair?token=$PAIRING_TOKEN")

        assertEquals(DeepLinkAction.Pair(PAIRING_TOKEN), action)
    }

    @Test
    fun null_uri_routes_to_unknown_so_a_cold_launch_with_no_data_doesnt_crash() {
        // Standard launcher path: user opens the app from the home
        // screen icon, Intent.data is null. Router must tolerate this
        // — calling code uses Unknown to mean "no deep link, just
        // route to the default landing screen".
        val action = DeepLinkRouter.route(null)

        assertEquals(DeepLinkAction.Unknown, action)
    }

    @Test
    fun unknown_lap_scheme_route_returns_unknown_so_lap_settings_doesnt_pretend_to_be_a_pair_url() {
        // Forward-compat: future lap://settings or lap://share routes
        // shouldn't accidentally satisfy "is this a pairing URL"
        // just because the scheme matches. Each new route adds its
        // own variant.
        val action = DeepLinkRouter.route("lap://settings?token=$OTHER_TOKEN")

        assertEquals(DeepLinkAction.Unknown, action)
    }

    @Test
    fun https_url_to_unrelated_host_routes_to_unknown() {
        val action = DeepLinkRouter.route("https://example.com/pair?token=$PAIRING_TOKEN")

        assertEquals(DeepLinkAction.Unknown, action)
    }

    @Test
    fun lap_pair_url_with_blank_token_routes_to_unknown_so_partial_url_doesnt_pretend_to_pair() {
        val action = DeepLinkRouter.route("lap://pair?token=")

        assertEquals(DeepLinkAction.Unknown, action)
    }

    @Test
    fun malformed_uri_routes_to_unknown_without_throwing() {
        val action = DeepLinkRouter.route("not a uri ::: %%%")

        assertEquals(DeepLinkAction.Unknown, action)
    }
}
