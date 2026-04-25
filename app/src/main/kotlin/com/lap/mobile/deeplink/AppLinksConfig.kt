package com.lap.mobile.deeplink

/**
 * Canonical Android App Links configuration for T185.
 *
 * Couples the AndroidManifest's https intent-filter, T178's
 * [com.lap.mobile.qr.QrPayloadClassifier] (which T184's
 * [DeepLinkRouter] composes), and the server-side `assetlinks.json`
 * to a single set of constants. A change to [HOST] or [PAIR_PATH]
 * that doesn't update those three places shows up immediately as a
 * coupling-test failure in `AppLinksConfigTest`.
 *
 * ## Server-side requirement (out of this repo)
 *
 * The OS performs domain verification at app-install time by fetching
 * `https://app.lap-coach.racing/.well-known/assetlinks.json`. That
 * file MUST exist and MUST contain (at minimum):
 *
 * ```json
 * [{
 *   "relation": ["delegate_permission/common.handle_all_urls"],
 *   "target": {
 *     "namespace": "android_app",
 *     "package_name": "com.lap.mobile",
 *     "sha256_cert_fingerprints": [
 *       "<DEBUG_KEYSTORE_SHA256>",
 *       "<RELEASE_KEYSTORE_SHA256>",
 *       "<PLAY_APP_SIGNING_SHA256>"
 *     ]
 *   }
 * }]
 * ```
 *
 * The Play App Signing fingerprint is the one the Play Store
 * substitutes after upload — without it, App Links break for users
 * who installed from Play. The debug fingerprint is for `adb
 * install`-side smoke testing during development.
 *
 * Get the debug fingerprint via:
 * ```
 * keytool -list -v -keystore ~/.android/debug.keystore \
 *   -alias androiddebugkey -storepass android -keypass android
 * ```
 *
 * Verification status can be inspected on a device with:
 * ```
 * adb shell pm get-app-links com.lap.mobile
 * ```
 *
 * "verified" status means the OS routes silently; "ask" means it
 * still shows the chooser.
 *
 * ## Why https only
 *
 * App Links protocol applies only to https URLs. The custom
 * `lap://pair?token=…` filter (T184) lives in the same MainActivity
 * but uses `autoVerify="false"` because no assetlinks-equivalent
 * exists for custom schemes. The two filters complement each other:
 * the https one is preferred when the device verifies the domain;
 * the lap-scheme one is the fallback for clients that emit the
 * shorter form.
 */
object AppLinksConfig {
    const val HOST: String = "app.lap-coach.racing"
    const val PAIR_PATH: String = "/pair"
    const val PAIR_URL_PREFIX: String = "https://app.lap-coach.racing/pair"
}
