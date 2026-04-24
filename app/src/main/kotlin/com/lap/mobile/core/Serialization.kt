package com.lap.mobile.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

/**
 * Project-wide SerializersModule. Polymorphic registrations for domain
 * sealed hierarchies (SourceTag, EnvironmentTag, ActionPrescription, …)
 * will be added here as Block B lands.
 */
val lapSerializersModule: SerializersModule = SerializersModule {
    // Register polymorphic hierarchies here as Block B lands, e.g.:
    // polymorphic(SourceTag::class) {
    //     subclass(SourceTag.IRacing::class)
    //     ...
    // }
}

/**
 * Project-wide Json instance. All JSON round-trips go through this so
 * the discriminator key (`@type`), unknown-key tolerance and default
 * encoding behaviour are configured consistently.
 */
val lapJson: Json = Json {
    serializersModule = lapSerializersModule
    classDiscriminator = "@type"
    encodeDefaults = false
    ignoreUnknownKeys = true
    isLenient = false
    explicitNulls = false
}
