package com.lap.mobile.deeplink

import org.junit.Assert.assertEquals
import org.junit.Test

private const val PAIRING_TOKEN: String = "p_01JX9Z8M4KTRP5D6Y3V2N7H8KQ"

/**
 * Locks T185 App Links contract: the host + path declared in
 * [AppLinksConfig] must match what [DeepLinkRouter] (T184) accepts AND
 * what the AndroidManifest's https intent-filter declares. If those
 * three drift, the OS verifies a domain the router can't decode (or
 * vice versa), and the App Link silently degrades to the chooser.
 *
 * The server-side assetlinks.json at
 * `https://app.lap-coach.racing/.well-known/assetlinks.json` is OUT
 * of this repo, but its `package_name` field must equal what
 * AndroidManifest declares — that's a coordination point worth a
 * comment in [AppLinksConfig] not a test, since we can't fetch the
 * remote JSON from a unit test.
 */
class AppLinksConfigTest {
    @Test
    fun pair_url_prefix_uses_https_so_app_links_protocol_can_verify_via_assetlinks_json() {
        // App Links protocol only applies to https — http URLs never
        // get autoVerify treatment from the OS regardless of the
        // manifest flag. Locking this catches a future "let's also
        // support staging.lap-coach.racing without TLS" edit.
        assertEquals("https", AppLinksConfig.PAIR_URL_PREFIX.substringBefore("://"))
    }

    @Test
    fun pair_url_prefix_targets_the_canonical_app_link_host() {
        assertEquals(AppLinksConfig.HOST, AppLinksConfig.PAIR_URL_PREFIX.substringAfter("://").substringBefore("/"))
    }

    @Test
    fun pair_url_prefix_includes_the_pair_path() {
        assertEquals(AppLinksConfig.PAIR_PATH, AppLinksConfig.PAIR_URL_PREFIX.substringAfter(AppLinksConfig.HOST))
    }

    @Test
    fun pair_url_prefix_with_token_routes_to_pair_action_via_T184_router() {
        // Coupling test: any change to AppLinksConfig.PAIR_URL_PREFIX
        // that doesn't update T178's QrPayloadClassifier (which T184
        // routes through) immediately fails here. This is the
        // canonical "the manifest filter, the constant, and the
        // router all agree" invariant.
        val action = DeepLinkRouter.route("${AppLinksConfig.PAIR_URL_PREFIX}?token=$PAIRING_TOKEN")

        assertEquals(DeepLinkAction.Pair(PAIRING_TOKEN), action)
    }
}
