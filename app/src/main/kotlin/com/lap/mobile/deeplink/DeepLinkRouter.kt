package com.lap.mobile.deeplink

import com.lap.mobile.qr.QrPayload
import com.lap.mobile.qr.QrPayloadClassifier

/**
 * Routing actions an inbound deep link can resolve to (T184).
 *
 * Distinct from [QrPayload] (T178) because deep links have a broader
 * future domain — `lap://share?session=…`, `lap://settings/x`,
 * etc. — that we don't want to fold into the QR parser's tighter
 * surface. Keeping [DeepLinkAction] as the routing type lets future
 * tasks add variants here without changing the QR contract.
 */
sealed interface DeepLinkAction {
    /** Pairing flow entry point — webapp emitted `lap://pair?token=…` or the App Link form. */
    data class Pair(val token: String) : DeepLinkAction

    /** No deep-link data, malformed URI, or a route we don't recognize. */
    data object Unknown : DeepLinkAction
}

/**
 * Routes an inbound deep-link URI string (typically from
 * [android.content.Intent.getData]'s `toString()`) to a
 * [DeepLinkAction] the activity layer dispatches on.
 *
 * Composes T178's [QrPayloadClassifier]: both webapp deep links and
 * scanned QR codes encode the same `lap://pair?token=…` /
 * `https://app.lap-coach.racing/pair?token=…` shapes (T185 declares
 * the latter as an App Link), so they share a parser. Change the URL
 * format once, both surfaces update.
 *
 * Null input (cold launch with no data) returns [DeepLinkAction.Unknown];
 * the activity treats Unknown as "route to default landing screen,
 * no deep-link override".
 */
object DeepLinkRouter {
    fun route(rawUri: String?): DeepLinkAction = when (val payload = QrPayloadClassifier.classify(rawUri)) {
        is QrPayload.PairingToken -> DeepLinkAction.Pair(payload.token)
        QrPayload.Unknown -> DeepLinkAction.Unknown
    }
}
